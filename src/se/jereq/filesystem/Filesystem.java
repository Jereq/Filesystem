package se.jereq.filesystem;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import se.jereq.filesystem.INode.Type;

public class Filesystem
{
	public static final short ROOT_BLOCK = 0;
	public static final short FREE_LIST_BLOCK = 1;
	public static final short BLOCK_START = 2;
	
	private BlockDevice m_BlockDevice;

	private List<String> currentDirectory;
	private int currentNode = -1;

	public Filesystem(BlockDevice p_BlockDevice)
	{
		m_BlockDevice = p_BlockDevice;
	}

	public String format()
	{
		INode root = new INode("�SYSTEM_ROOT_NODE", INode.Type.Directory);
		writeINode(ROOT_BLOCK, root);
		
		FreeListNode free = new FreeListNode();
		writeFreeList(free);
		
		currentDirectory = new ArrayList<String>();
		currentNode = 0;
		
		return new String("Diskformat successful");
	}
	
	private void printRep(short val, int rep)
	{
		System.out.print("[" + val + "]");
		
		if (rep != 1)
			System.out.println(" x" + rep);
		else
			System.out.println();
	}
	
	private void dumpChildren(INode node)
	{
		short prev = node.getChild(0);
		int repCount = 1;
		
		for (int i = 1; i < INode.NUM_CHILDREN; ++i)
		{
			short val = node.getChild(i);
			
			if (val == prev)
			{
				repCount++;
			}
			else
			{
				printRep(prev, repCount);
				repCount = 1;
				prev = val;
			}
		}
		
		printRep(prev, repCount);
	}
	
	@SuppressWarnings("unused")
	private void dumpINode(INode node)
	{
		System.out.println("Name: \"" + node.getName() + "\"");
		System.out.println("Type: " + node.getType());
		
		dumpChildren(node);
	}
	
	private short findChildNode(INode current, String next)
	{
		if (current.getType() == Type.File)
			return -1;
		
		int i = 0;
		short currChild = current.getChild(i++);
		while (currChild != -1)
		{
			INode child = new INode(m_BlockDevice.readBlock(currChild));
			
			if (child.getName().equals(next))
				return currChild;
			
			currChild = current.getChild(i++);
		}
		
		return -1;
	}
	
	private INode findChildINode(INode current, String next)
	{
		if (current.getType() == Type.File)
			return null;
		
		int i = 0;
		int currChild = current.getChild(i++);
		while (currChild != -1)
		{
			INode child = new INode(m_BlockDevice.readBlock(currChild));
			
			if (child.getName().equals(next))
				return child;
			
			currChild = current.getChild(i++);
		}
		
		return null;
	}
	
	private String[] toAbsolute(String[] path)
	{
		if (path == null || path.length == 0)
			return currentDirectory.toArray(new String[0]);
		
		List<String> absolutePath;
		if ("".equals(path[0]))
			absolutePath = new ArrayList<String>();
		else
			absolutePath = new ArrayList<String>(currentDirectory);
		
		for (String s : path)
		{
			if ("".equals(s) || ".".equals(s))
				continue;
			else if ("..".equals(s))
			{
				if (!absolutePath.isEmpty())
					absolutePath.remove(absolutePath.size() - 1);
				else
					return new String[0];	// Faulty path, return a default value
			}
			else
				absolutePath.add(s);
		}
		
		return absolutePath.toArray(new String[0]);
	}
	
	private short findNode(String[] path)
	{
		String[] absolutePath = toAbsolute(path);
		
		if (absolutePath.length == 0)
			return ROOT_BLOCK;
		
		INode node = new INode(m_BlockDevice.readBlock(ROOT_BLOCK));
		for (int i = 0; i < absolutePath.length - 1; ++i)
		{
			node = findChildINode(node, absolutePath[i]);
			
			if (node == null)
				return -1;
		}
		
		return findChildNode(node, absolutePath[absolutePath.length - 1]);
	}
	
	private INode findINode(String[] path)
	{
		String[] absolutePath = toAbsolute(path);
		
		INode node = new INode(m_BlockDevice.readBlock(ROOT_BLOCK));
		for (String s : absolutePath)
		{
			node = findChildINode(node, s);
			
			if (node == null)
				return null;
		}
		
		return node;
	}

	public String ls(String[] p_asPath)
	{
		if (currentNode < 0)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";
		
		INode dir = findINode(p_asPath);
		
		if (dir == null)
		{
			return "Directory does not exist";
		}
		
		if (dir.getType() == Type.File)
		{
			return "Cannot list file";
		}
		
		StringBuilder res = new StringBuilder("Listing directory ");
		res.append(concatPath(toAbsolute(p_asPath))).append("/");
		
		res.append("\n\n");
		
		int i = 0;
		int currChild = dir.getChild(i++);
		while (currChild != -1)
		{
			INode child = new INode(m_BlockDevice.readBlock(currChild));
			
			res.append(String.format("%-20s%-10s%10d\n", child.getName(), child.getType(), child.getSize()));
			
			currChild = dir.getChild(i++);
		}
		
		if (i == 1)
		{
			res.append("Empty directory");
		}
		
		return res.toString();
	}

	private FreeListNode getFreeList()
	{
		return new FreeListNode(m_BlockDevice.readBlock(FREE_LIST_BLOCK));
	}
	
	private void writeFreeList(FreeListNode freeList)
	{

		m_BlockDevice.writeBlock(FREE_LIST_BLOCK, freeList.getBlock());
	}
	
	private INode getINode(short num)
	{
		return new INode(m_BlockDevice.readBlock(num));
	}
	
	private void writeINode(short num, INode node)
	{
		m_BlockDevice.writeBlock(num, node.getBlock());
	}
	
	private void copyBlock(short source, short dest)
	{
		byte[] data = m_BlockDevice.readBlock(source);
		m_BlockDevice.writeBlock(dest, data);
	}
	
	public String create(String[] p_asPath,byte[] p_abContents)
	{
		if (currentNode < 0)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";

		if (p_asPath == null || p_asPath.length == 0)
			return "Invalid path";
		
		String filename = p_asPath[p_asPath.length - 1];
		if (filename == null || filename.isEmpty())
			return "Invalid filename";
		
		short parentNum = findNode(Arrays.copyOfRange(p_asPath, 0, p_asPath.length - 1));
		if (parentNum == -1)
			return "Invalid path";
		
		INode parentNode = getINode(parentNum);
		if (findChildNode(parentNode, filename) != -1)
			return "A file or directory with that name already exists. Delete that file first or choose another name.";
		
		if (p_abContents.length > BlockDevice.BLOCK_SIZE * INode.NUM_CHILDREN)
			return "Size to large. Max filesize supported is " + (BlockDevice.BLOCK_SIZE * INode.NUM_CHILDREN) + " bytes";
		
		FreeListNode free = getFreeList();
		
		short fileNum = free.getNewBlock();
		
		INode fileNode;
		try
		{
			fileNode = new INode(filename, Type.File);
		}
		catch (IllegalArgumentException ex)
		{
			return ex.getMessage();
		}
		
		for (int i = 0; i < p_abContents.length; i += BlockDevice.BLOCK_SIZE)
		{
			short blockNum = free.getNewBlock();
			byte[] blockOfData = Arrays.copyOfRange(p_abContents, i, i + BlockDevice.BLOCK_SIZE);
			
			m_BlockDevice.writeBlock(blockNum, blockOfData);
			fileNode.addChild(blockNum);
		}
		
		fileNode.setSize(p_abContents.length);
		
		parentNode.addChild(fileNum);
		parentNode.setSize(parentNode.getSize() + 1);
		
		writeINode(fileNum, fileNode);
		
		// Finalize changes
		writeINode(parentNum, parentNode);
		writeFreeList(free);

		return concatPath(p_asPath) + " created successfully";
	}

	public String cat(String[] p_asPath)
	{
		if (currentNode < 0)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";
		
		INode file = findINode(p_asPath);
		
		if (file == null)
		{
			return "File does not exist";
		}
		
		if (file.getType() != Type.File)
		{
			return "Cannot catenate anything other than files";
		}
		
		int fileSize = file.getSize();
		int completeBlocks = fileSize / BlockDevice.BLOCK_SIZE;
		int incompleteBlockSize = fileSize % BlockDevice.BLOCK_SIZE;
		
		StringBuilder res = new StringBuilder();
		res.append("Dumping contents of ").append(file.getName())
			.append(" (").append(file.getSize()).append(" bytes):\n");
		
		for (int i = 0; i < completeBlocks; ++i)
		{
			short blockNum = file.getChild(i);
			byte[] block = m_BlockDevice.readBlock(blockNum);
			res.append(new String(block, 0, block.length));
		}
		
		if (incompleteBlockSize != 0)
		{
			short blockNum = file.getChild(completeBlocks);
			byte[] block = m_BlockDevice.readBlock(blockNum);
			res.append(new String(block, 0, incompleteBlockSize));
		}
		
		return res.toString();
	}

	public String save(String p_sPath)
	{
		if (currentNode < 0)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";
		
		try
		{
			OutputStream output = null;
			try
			{
				output = new BufferedOutputStream(new FileOutputStream(p_sPath));
				for (int i = 0; i < BlockDevice.BLOCK_COUNT; ++i)
				{
					output.write(m_BlockDevice.readBlock(i));
				}
			}
			finally
			{
				output.close();
			}
		}
		catch (FileNotFoundException ex)
		{
			return "File not found";
		}
		catch (IOException ex)
		{
			return ex.toString();
		}
		
		return "Saved blockdevice to file " + p_sPath;
	}

	public String read(String p_sPath)
	{
		File file = new File(p_sPath);
		
		if (file.length() != BlockDevice.BLOCK_COUNT * BlockDevice.BLOCK_SIZE)
		{
			return "Invalid file size";
		}
		
		try
		{
			InputStream input = null;
			try
			{
				byte[] block = new byte[BlockDevice.BLOCK_SIZE];
				
				input = new BufferedInputStream(new FileInputStream(file));
				for (int i = 0; i < BlockDevice.BLOCK_COUNT; ++i)
				{
					int totalBytesRead = 0;
					
					while (totalBytesRead < block.length)
					{
						int bytesRemaining = block.length - totalBytesRead;
						int bytesRead = input.read(block, totalBytesRead, bytesRemaining);
						if (bytesRead > 0)
							totalBytesRead += bytesRead;
					}
					
					m_BlockDevice.writeBlock(i, block);
				}
			}
			finally
			{
				input.close();
			}
		}
		catch (FileNotFoundException ex)
		{
			return "File not found";
		}
		catch (IOException ex)
		{
			return ex.toString();
		}
		
		currentDirectory = new ArrayList<String>();
		currentNode = 0;
		
		return "Read file " + p_sPath + " to blockdevice";
	}

	public String rm(String[] p_asPath)
	{
		if (currentNode < 0)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";
		
		if (p_asPath == null || p_asPath.length == 0)
			return "Invalid path";
		
		String filename = p_asPath[p_asPath.length - 1];
		if (filename == null || filename.isEmpty())
			return "Invalid filename";
		
		short parentNum = findNode(Arrays.copyOfRange(p_asPath, 0, p_asPath.length - 1));
		if (parentNum == -1)
			return "Invalid path";
		
		INode parentNode = getINode(parentNum);
		
		short fileNum = findChildNode(parentNode, filename);
		if (fileNum == -1)
			return "File does not exist";
		
		INode node = getINode(fileNum);
		
		FreeListNode free = getFreeList();
		
		switch (node.getType())
		{
		case File:
			{
				parentNode.removeChildByVal(fileNum);
				
				int childId = 0;
				short blockNum = node.getChild(childId++);
				while (blockNum != -1)
				{
					free.freeBlock(blockNum);
					blockNum = node.getChild(childId++);
				}

				parentNode.setSize(parentNode.getSize() - 1);
				
				free.freeBlock(fileNum);
				
				// Finalize changes
				writeINode(parentNum, parentNode);
				writeFreeList(free);
				
				return "Deleted file " + filename;
			}
			
		case Directory:
			{
				if (node.getSize() != 0)
					return "Cannot remove non-empty directory";
				
				parentNode.removeChildByVal(fileNum);
				parentNode.setSize(parentNode.getSize() - 1);
				
				free.freeBlock(fileNum);
				
				// Finalize changes
				writeINode(parentNum, parentNode);
				writeFreeList(free);
				
				return "Deleted directory " + filename;
			}
			
		default:
			return "Error: unknown type";
		}
	}

	private short copyFile(INode source, String destName, FreeListNode freeList)
	{
		short destFileNum = freeList.getNewBlock();
		INode destFileNode = new INode(destName, Type.File);
		
		int blockId = 0;
		short blockNum = source.getChild(0);
		while (blockNum != -1)
		{
			short newBlockNum = freeList.getNewBlock();
			
			copyBlock(blockNum, newBlockNum);
			
			destFileNode.addChild(newBlockNum);
			
			blockNum = source.getChild(++blockId);
		}
		
		destFileNode.setSize(source.getSize());
		
		writeINode(destFileNum, destFileNode);
		
		return destFileNum;
	}
	
	private short copyDir(INode source, String destName, FreeListNode freeList)
	{
		short destDirNum = freeList.getNewBlock();
		INode destDirNode = new INode(destName, Type.Directory);
		
		int childId = 0;
		short childNum = source.getChild(0);
		while (childNum != -1)
		{
			INode childNode = getINode(childNum);
			short childCopyNum = copy(childNode, childNode.getName(), freeList);
			destDirNode.addChild(childCopyNum);
			
			childNum = source.getChild(++childId);
		}
		
		destDirNode.setSize(source.getSize());
		
		writeINode(destDirNum, destDirNode);
		
		return destDirNum;
	}
	
	private short copy(INode source, String destName, FreeListNode freeList)
	{
		switch (source.getType())
		{
		case File:
			return copyFile(source, destName, freeList);
			
		case Directory:
			return copyDir(source, destName, freeList);
			
		default:
			return -1;
		}
	}
	
	public String copy(String[] p_asSource, String[] p_asDestination)
	{
		if (currentNode < 0)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";

		if (p_asSource == null || p_asSource.length == 0)
			return "Invalid source path";
		
		INode sourceNode = findINode(p_asSource);
		if (sourceNode == null)
			return "Source does not exist";

		if (p_asDestination == null || p_asDestination.length == 0)
			return "Invalid destination path";
		
		String destFilename = p_asDestination[p_asDestination.length - 1];
		if (destFilename == null || destFilename.isEmpty())
			return "Invalid destination filename";
		
		short destParentNum = findNode(Arrays.copyOfRange(p_asDestination, 0, p_asDestination.length - 1));
		if (destParentNum == -1)
			return "Invalid destination path";
		
		INode destParentNode = getINode(destParentNum);
		if (findChildNode(destParentNode, destFilename) != -1)
			return "A file or directory with the destination name already exists. Delete that file first or choose another name.";
		
		FreeListNode free = getFreeList();
		
		short copyNum = copy(sourceNode, destFilename, free);
		if (copyNum == -1)
			return "Could not copy file or directory";
		
		destParentNode.addChild(copyNum);
		destParentNode.setSize(destParentNode.getSize() + 1);
		
		// Finalize changes
		writeINode(destParentNum, destParentNode);
		writeFreeList(free);

		return concatPath(p_asSource) + " copied successfully to " + concatPath(p_asDestination);
	}
	
	private void appendDirect(INode sourceFile, INode destFile, FreeListNode freeList)
	{
		int blockId = 0;
		short blockNum = sourceFile.getChild(0);
		while (blockNum != -1)
		{
			short newBlock = freeList.getNewBlock();
			copyBlock(blockNum, newBlock);
			destFile.addChild(newBlock);
			
			blockNum = sourceFile.getChild(++blockId);
		}
	}
	
	private void bufferedCopy(short source, short dest, byte[] buffer, int devideAt)
	{
		byte[] sourceBlock = m_BlockDevice.readBlock(source);
		System.arraycopy(sourceBlock, 0, buffer, devideAt, buffer.length - devideAt);
		
		m_BlockDevice.writeBlock(dest, buffer);
		
		System.arraycopy(sourceBlock, buffer.length - devideAt, buffer, 0, devideAt);
	}
	
	private void appendBuffered(INode sourceFile, INode destFile, FreeListNode freeList)
	{
		int sourceSize = sourceFile.getSize();
		if (sourceSize == 0)
			return;
		
		int sourceLastPartSize = sourceSize % BlockDevice.BLOCK_SIZE;
		
		int destSize = destFile.getSize();
		int destStartBlock = destSize / BlockDevice.BLOCK_SIZE;
		int firstPartSize = destSize % BlockDevice.BLOCK_SIZE;
		int sndPartSize = BlockDevice.BLOCK_SIZE - firstPartSize;
		
		short firstDestBlock = destFile.getChild(destStartBlock);
		byte[] buffer = m_BlockDevice.readBlock(firstDestBlock);
		
		int blockId = 0;
		short blockNum = sourceFile.getChild(0);
		
		// Write back the existing first block
		{
			bufferedCopy(blockNum, firstDestBlock, buffer, firstPartSize);
			
			blockNum = sourceFile.getChild(++blockId);
		}
		
		// Write middle blocks
		while (blockNum != -1)
		{
			short newBlock = freeList.getNewBlock();
			
			bufferedCopy(blockNum, newBlock, buffer, firstPartSize);
			
			destFile.addChild(newBlock);
			
			blockNum = sourceFile.getChild(++blockId);
		}
		
		// Write any potentially remaining data in the buffer
		if (sourceLastPartSize > sndPartSize)
		{
			short newBlock = freeList.getNewBlock();
			m_BlockDevice.writeBlock(newBlock, buffer);
			
			destFile.addChild(newBlock);
		}
	}

	public String append(String[] p_asSource, String[] p_asDestination)
	{
		if (currentNode < 0)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";
		
		if (p_asSource == null || p_asSource.length == 0)
			return "Invalid source path";
		
		INode sourceFileNode = findINode(p_asSource);
		if (sourceFileNode == null)
			return "Source does not exist";
		
		if (sourceFileNode.getType() != Type.File)
			return "Source is not a file";
		
		if (p_asDestination == null || p_asDestination.length == 0)
			return "Invalid destination path";
		
		short destFileNum = findNode(p_asDestination);
		if (destFileNum == -1)
			return "Destination does not exist";
		
		INode destFileNode = getINode(destFileNum);
		if (destFileNode.getType() != Type.File)
			return "Destination is not a file";
		
		int destStartSize = destFileNode.getSize();
		int newSize = destStartSize + sourceFileNode.getSize();
		
		if (newSize > BlockDevice.BLOCK_SIZE * INode.NUM_CHILDREN)
			return "Files to large, can not append";
		
		FreeListNode free = getFreeList();
		
		if (destStartSize % BlockDevice.BLOCK_SIZE == 0)
			appendDirect(sourceFileNode, destFileNode, free);
		else
			appendBuffered(sourceFileNode, destFileNode, free);
		
		destFileNode.setSize(newSize);
		
		writeINode(destFileNum, destFileNode);
		writeFreeList(free);

		return "Appended " + concatPath(p_asSource) + " to " + concatPath(p_asDestination);
	}

	public String rename(String[] p_asSource,String[] p_asDestination)
	{
		if (currentNode < 0)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";

		if (p_asSource == null || p_asSource.length == 0)
			return "Invalid source path";

		if (p_asDestination == null || p_asDestination.length == 0)
			return "Invalid destination path";
		
		short sourceParentNum = findNode(Arrays.copyOfRange(p_asSource, 0, p_asSource.length - 1));
		if (sourceParentNum == -1)
			return "Invalid source path";
		
		short destParentNum = findNode(Arrays.copyOfRange(p_asDestination, 0, p_asDestination.length - 1));
		if (destParentNum == -1)
			return "Invalid destination path";
		
		String sourceFilename = p_asSource[p_asSource.length - 1];
		if (sourceFilename == null || sourceFilename.isEmpty())
			return "Invalid source filename";
		
		String destFilename = p_asDestination[p_asDestination.length - 1];
		if (destFilename == null || destFilename.isEmpty())
			return "Invalid destination filename";
		
		
		INode sourceParentNode = getINode(sourceParentNum);
		short sourceNum = findChildNode(sourceParentNode, sourceFilename);
		if (sourceNum == -1)
			return "Source does not exist";
		INode sourceNode = getINode(sourceNum);
		
		INode destParentNode = getINode(destParentNum);
		if (findChildNode(destParentNode, destFilename) != -1)
			return "A file or directory with the destination name already exists. Delete that file first or choose another name.";
		
		// Rename if names different
		if (!sourceFilename.equals(destFilename))
		{
			sourceNode.setName(destFilename);
			writeINode(sourceNum, sourceNode);
		}
		
		// Move file if in different places
		if (sourceParentNum != destParentNum)
		{
			sourceParentNode.removeChildByVal(sourceNum);
			sourceParentNode.setSize(sourceParentNode.getSize() - 1);
			
			destParentNode.addChild(sourceNum);
			destParentNode.setSize(destParentNode.getSize() + 1);
			
			// Finalize changes
			writeINode(destParentNum, destParentNode);
			writeINode(sourceParentNum, sourceParentNode);
		}

		return concatPath(p_asSource) + " renamed successfully to " + concatPath(p_asDestination);
	}

	public String mkdir(String[] p_asPath)
	{
		if (currentNode < 0)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";

		if (p_asPath == null || p_asPath.length == 0)
			return "Invalid path";
		
		String dirname = p_asPath[p_asPath.length - 1];
		if (dirname == null || dirname.isEmpty())
			return "Invalid filename";
		
		short parentNum = findNode(Arrays.copyOfRange(p_asPath, 0, p_asPath.length - 1));
		if (parentNum == -1)
			return "Invalid path";
		
		INode parentNode = getINode(parentNum);
		if (findChildNode(parentNode, dirname) != -1)
			return "A file or directory with that name already exists. Delete that file first or choose another name.";
		
		FreeListNode free = getFreeList();
		
		short dirNum = free.getNewBlock();
		INode dirNode = new INode(dirname, Type.Directory);
		
		parentNode.addChild(dirNum);
		parentNode.setSize(parentNode.getSize() + 1);
		
		writeINode(dirNum, dirNode);
		
		// Finalize changes
		writeINode(parentNum, parentNode);
		writeFreeList(free);

		return concatPath(p_asPath) + " created successfully";
	}

	public String cd(String[] p_asPath)
	{
		if (currentNode < 0)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";
		
		System.out.print("Changing directory to ");
		dumpArray(p_asPath);
		System.out.print("");
		return new String("");
	}

	public String pwd()
	{
		if (currentDirectory == null)
			return "Unformatted filesystem";
		
		StringBuilder res = new StringBuilder("/");
		for (String s : currentDirectory)
		{
			res.append(s).append('/');
		}
		return res.toString();
	}

	private void dumpArray(String[] p_asArray)
	{
		for(int nIndex = 0; nIndex < p_asArray.length; nIndex++)
		{
			System.out.print(p_asArray[nIndex] + "=>");
		}
	}
	
	private String concatPath(String[] path)
	{
		if (path == null || path.length == 0)
			return "";
		
		StringBuilder res = new StringBuilder();
		for (String p : path)
			res.append("/").append(p);
		
		return res.toString();
	}

}
