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
import java.util.Collections;
import java.util.List;

/**
 * A simple and limited filesystem using a provided {@link BlockDevice}.
 * <br><br>
 * Currently only supports statically defined blocks sizes and block counts.
 */
public class Filesystem
{
	private static final short ROOT_BLOCK = 0;
	private static final short FREE_LIST_BLOCK = 1;
	
	/**
	 * The index of the first block free for dynamic use.
	 * Earlier blocks are reserved for use by the filesystem.
	 */
	public static final short BLOCK_START = 2;
	
	private BlockDevice m_BlockDevice;
	private List<String> currentDirectory;

	/**
	 * constructor.
	 * 
	 * @param p_BlockDevice the block device to support the filesystem.
	 */
	public Filesystem(BlockDevice p_BlockDevice)
	{
		m_BlockDevice = p_BlockDevice;
	}

	/**
	 * Initializes an empty filesystem so that it is ready for use. Existing data
	 * is not overwritten, except for any blocks reserved by the filesystem.
	 * 
	 * @return A descriptive result from the operation, without final newline.
	 */
	public String format()
	{
		INode root = new INode("§SYSTEM_ROOT_NODE", INode.Type.Directory);
		writeINode(ROOT_BLOCK, root);
		
		FreeListNode free = new FreeListNode();
		writeFreeList(free);
		
		currentDirectory = Collections.emptyList();
		
		return new String("Diskformat successful");
	}
	
	private short findChildNode(INode current, String nextName)
	{
		if (current.getType() == INode.Type.File)
			return -1;
		
		int i = 0;
		short currChild = current.getChild(i++);
		while (currChild != -1)
		{
			INode child = new INode(m_BlockDevice.readBlock(currChild));
			
			if (child.getName().equals(nextName))
				return currChild;
			
			currChild = current.getChild(i++);
		}
		
		return -1;
	}
	
	private INode findChildINode(INode current, String nextName)
	{
		if (current.getType() == INode.Type.File)
			return null;
		
		int i = 0;
		int currChild = current.getChild(i++);
		while (currChild != -1)
		{
			INode child = new INode(m_BlockDevice.readBlock(currChild));
			
			if (child.getName().equals(nextName))
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
	
	private short findNode(String[] absPath)
	{
		if (absPath.length == 0)
			return ROOT_BLOCK;
		
		INode node = new INode(m_BlockDevice.readBlock(ROOT_BLOCK));
		for (int i = 0; i < absPath.length - 1; ++i)
		{
			node = findChildINode(node, absPath[i]);
			
			if (node == null)
				return -1;
		}
		
		return findChildNode(node, absPath[absPath.length - 1]);
	}
	
	private INode findINode(String[] absPath)
	{
		INode node = new INode(m_BlockDevice.readBlock(ROOT_BLOCK));
		for (String s : absPath)
		{
			node = findChildINode(node, s);
			
			if (node == null)
				return null;
		}
		
		return node;
	}

	/**
	 * Creates a list of the contents in a directory given by path.
	 * 
	 * @param p_asPath the directory to list content from. Can be either relative or absolute.
	 * Absolute paths are marked by a leading empty string.
	 * 
	 * @return A descriptive result from the operation, without final newline.
	 * If successful, contains a formatted list of the contents in the directory.
	 */
	public String ls(String[] p_asPath)
	{
		if (currentDirectory == null)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";
		
		String[] absPath = toAbsolute(p_asPath);
		INode dir = findINode(absPath);
		
		if (dir == null)
		{
			return "Directory does not exist";
		}
		
		if (dir.getType() == INode.Type.File)
		{
			return "Can not list file";
		}
		
		StringBuilder res = new StringBuilder("Listing directory ");
		res.append(concatPath(p_asPath));
		
		res.append("\n\n");
		
		if (dir.getSize() == 0)
		{
			res.append("Empty directory");
			return res.toString();
		}
		
		res.append(String.format("%-20s%-10s%10s\n\n", "Name", "Type", "Size"));
		
		int i = 0;
		int currChild = dir.getChild(i++);
		while (currChild != -1)
		{
			INode child = new INode(m_BlockDevice.readBlock(currChild));
			
			res.append(String.format("%-20s%-10s%10d\n", child.getName(), child.getType(), child.getSize()));
			
			currChild = dir.getChild(i++);
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
	
	/**
	 * Create a new file with the provided content. Fails if the path is invalid or the content is to large.
	 * 
	 * @param p_asPath the path to the file to be created. Can be either relative or absolute.
	 * Absolute paths are marked by a leading empty string.
	 * 
	 * @param p_abContents a byte array containing the data to initialize the file with.
	 * @return A descriptive result from the operation, without final newline.
	 */
	public String create(String[] p_asPath, byte[] p_abContents)
	{
		if (currentDirectory == null)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";

		if (p_asPath == null || p_asPath.length == 0)
			return "Invalid path";
		
		String[] absPath = toAbsolute(p_asPath);
		
		String filename = absPath[absPath.length - 1];
		if (filename == null || filename.isEmpty())
			return "Invalid filename";
		
		short parentNum = findNode(Arrays.copyOfRange(absPath, 0, absPath.length - 1));
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
			fileNode = new INode(filename, INode.Type.File);
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

	/**
	 * Despite what the name says, this function does not catenate anything. Rather it
	 * returns the content of the single file the given file points to.
	 * 
	 * @param p_asPath the path to the file to be catenated. Can be either relative or absolute.
	 * Absolute paths are marked by a leading empty string.
	 * 
	 * @return If the path points to a file, returns the contents of that file, otherwise returns
	 * a descriptive error string. No final newline added.
	 */
	public String cat(String[] p_asPath)
	{
		if (currentDirectory == null)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";
		
		String[] absPath = toAbsolute(p_asPath);
		
		INode file = findINode(absPath);
		
		if (file == null)
		{
			return "File does not exist";
		}
		
		if (file.getType() != INode.Type.File)
		{
			return "Can not catenate anything other than files";
		}
		
		int fileSize = file.getSize();
		int completeBlocks = fileSize / BlockDevice.BLOCK_SIZE;
		int incompleteBlockSize = fileSize % BlockDevice.BLOCK_SIZE;
		
		StringBuilder res = new StringBuilder();
		res.append("Dumping contents of ").append(concatPath(p_asPath))
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

	/**
	 * Save this filesystem to a file in the real filesystem. 
	 * 
	 * @param p_sPath the path to the file where the data should be saved.
	 * Must follow java's rules for filenames.
	 *  
	 * @return A descriptive result from the operation, without final newline.
	 */
	public String save(String p_sPath)
	{
		if (currentDirectory == null)
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

	/**
	 * Initialize this filesystem with data from an actual file.
	 * 
	 * @param p_sPath the real file to read from. The file must be of the correct size.
	 * @return A descriptive result from the operation, without final newline.
	 */
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
		
		currentDirectory = Collections.emptyList();
		
		return "Read file " + p_sPath + " to blockdevice";
	}

	/**
	 * Remove target file or empty directory.
	 * 
	 * @param p_asPath the path to the file to be removed. Can be either relative or absolute.
	 * Absolute paths are marked by a leading empty string.
	 * 
	 * @return A descriptive result from the operation, without final newline.
	 */
	public String rm(String[] p_asPath)
	{
		if (currentDirectory == null)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";
		
		if (p_asPath == null || p_asPath.length == 0)
			return "Invalid path";
		
		String[] absPath = toAbsolute(p_asPath);
		
		String filename = absPath[absPath.length - 1];
		if (filename == null || filename.isEmpty())
			return "Invalid filename";
		
		short parentNum = findNode(Arrays.copyOfRange(absPath, 0, absPath.length - 1));
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
				
				return "Deleted file " + concatPath(p_asPath);
			}
			
		case Directory:
			{
				if (node.getSize() != 0)
					return "Can not remove non-empty directory";
				
				if (currentDirectory.equals(Arrays.asList(absPath)))
					return "Can not remove the working directory";
				
				parentNode.removeChildByVal(fileNum);
				parentNode.setSize(parentNode.getSize() - 1);
				
				free.freeBlock(fileNum);
				
				// Finalize changes
				writeINode(parentNum, parentNode);
				writeFreeList(free);
				
				return "Deleted directory " + concatPath(p_asPath);
			}
			
		default:
			return "Error: unknown type";
		}
	}

	private short copyFile(INode source, String destName, FreeListNode freeList)
	{
		short destFileNum = freeList.getNewBlock();
		INode destFileNode = new INode(destName, INode.Type.File);
		
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
		INode destDirNode = new INode(destName, INode.Type.Directory);
		
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
	
	/**
	 * Copy the target source file or directory to the target destination file or directory. 
	 * Will not overwrite any existing file or directory.
	 * 
	 * @param p_asSource the path to the file or directory to be copied. Can be either relative or absolute.
	 * Absolute paths are marked by a leading empty string.
	 * 
	 * @param p_asDestination the path to where the copy should be placed. Can be either relative or absolute.
	 * Absolute paths are marked by a leading empty string.
	 * 
	 * @return A descriptive result from the operation, without final newline.
	 */
	public String copy(String[] p_asSource, String[] p_asDestination)
	{
		if (currentDirectory == null)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";

		if (p_asSource == null || p_asSource.length == 0)
			return "Invalid source path";
		
		String[] absSource = toAbsolute(p_asSource);
		
		INode sourceNode = findINode(absSource);
		if (sourceNode == null)
			return "Source does not exist";
		
		if (p_asDestination == null || p_asDestination.length == 0)
			return "Invalid destination path";

		String[] absDest = toAbsolute(p_asDestination);
		
		String destFilename = absDest[absDest.length - 1];
		if (destFilename == null || destFilename.isEmpty())
			return "Invalid destination filename";
		
		short destParentNum = findNode(Arrays.copyOfRange(absDest, 0, absDest.length - 1));
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

	/**
	 * Append one file to the end of another file.
	 * 
	 * @param p_asSource the path to the file to be appended to the other. Can be either relative or absolute.
	 * Absolute paths are marked by a leading empty string.
	 * 
	 * @param p_asDestination the path to the file to be appended to. Can be either relative or absolute.
	 * Absolute paths are marked by a leading empty string.
	 * 
	 * @return A descriptive result from the operation, without final newline.
	 */
	public String append(String[] p_asSource, String[] p_asDestination)
	{
		if (currentDirectory == null)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";
		
		if (p_asSource == null || p_asSource.length == 0)
			return "Invalid source path";
		
		String[] absSource = toAbsolute(p_asSource);
		
		INode sourceFileNode = findINode(absSource);
		if (sourceFileNode == null)
			return "Source does not exist";
		
		if (sourceFileNode.getType() != INode.Type.File)
			return "Source is not a file";
		
		if (p_asDestination == null || p_asDestination.length == 0)
			return "Invalid destination path";
		
		String[] absDest = toAbsolute(p_asDestination);
		
		short destFileNum = findNode(absDest);
		if (destFileNum == -1)
			return "Destination does not exist";
		
		INode destFileNode = getINode(destFileNum);
		if (destFileNode.getType() != INode.Type.File)
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

	/**
	 * Rename a file or directory. Will also move the file if different
	 * parent directories are specified. Will not overwrite files or directories.
	 * 
	 * @param p_asSource the path to the file or directory to rename. Can be either relative or absolute.
	 * Absolute paths are marked by a leading empty string.
	 * 
	 * @param p_asDestination the new name and/or location. Can be either relative or absolute.
	 * Absolute paths are marked by a leading empty string.
	 * 
	 * @return A descriptive result from the operation, without final newline.
	 */
	public String rename(String[] p_asSource, String[] p_asDestination)
	{
		if (currentDirectory == null)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";

		if (p_asSource == null || p_asSource.length == 0)
			return "Invalid source path";

		if (p_asDestination == null || p_asDestination.length == 0)
			return "Invalid destination path";
		
		String[] absSource = toAbsolute(p_asSource);
		String[] absDest = toAbsolute(p_asDestination);
		
		short sourceParentNum = findNode(Arrays.copyOfRange(absSource, 0, absSource.length - 1));
		if (sourceParentNum == -1)
			return "Invalid source path";
		
		short destParentNum = findNode(Arrays.copyOfRange(absDest, 0, absDest.length - 1));
		if (destParentNum == -1)
			return "Invalid destination path";
		
		String sourceFilename = absSource[absSource.length - 1];
		if (sourceFilename == null || sourceFilename.isEmpty())
			return "Invalid source filename";
		
		String destFilename = absDest[absDest.length - 1];
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

	/**
	 * Create a directory.
	 * 
	 * @param p_asPath the path to the new directory. Can be either relative or absolute.
	 * Absolute paths are marked by a leading empty string.
	 * 
	 * @return A descriptive result from the operation, without final newline.
	 */
	public String mkdir(String[] p_asPath)
	{
		if (currentDirectory == null)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";

		if (p_asPath == null || p_asPath.length == 0)
			return "Invalid path";
		
		String[] absPath = toAbsolute(p_asPath);
		
		String dirname = absPath[absPath.length - 1];
		if (dirname == null || dirname.isEmpty())
			return "Invalid filename";
		
		short parentNum = findNode(Arrays.copyOfRange(absPath, 0, absPath.length - 1));
		if (parentNum == -1)
			return "Invalid path";
		
		INode parentNode = getINode(parentNum);
		if (findChildNode(parentNode, dirname) != -1)
			return "A file or directory with that name already exists. Delete that file first or choose another name.";
		
		FreeListNode free = getFreeList();
		
		short dirNum = free.getNewBlock();
		INode dirNode = new INode(dirname, INode.Type.Directory);
		
		parentNode.addChild(dirNum);
		parentNode.setSize(parentNode.getSize() + 1);
		
		writeINode(dirNum, dirNode);
		
		// Finalize changes
		writeINode(parentNum, parentNode);
		writeFreeList(free);

		return concatPath(p_asPath) + " created successfully";
	}

	/**
	 * Changes the working directory.
	 * 
	 * @param p_asPath the path to the new working directory. Can be either relative or absolute.
	 * Absolute paths are marked by a leading empty string.
	 * 
	 * @return A descriptive result from the operation, without final newline.
	 */
	public String cd(String[] p_asPath)
	{
		if (currentDirectory == null)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";
		
		if (p_asPath == null || p_asPath.length == 0)
			return "Invalid path";
		
		String[] absPath = toAbsolute(p_asPath);
		
		INode dir = findINode(absPath);
		if (dir == null)
			return "Directory does not exist";
		
		if (dir.getType() != INode.Type.Directory)
			return "Can not navigate to path, as path is not a directory";
		
		currentDirectory = Arrays.asList(absPath);
		
		return "Changed directory to /" + concatPath(absPath);
	}

	/**
	 * Returns the current working directory as an absolute path.
	 * 
	 * @return a string representing the current working directory.
	 */
	public String pwd()
	{
		if (currentDirectory == null)
			return "Unformatted filesystem";
		
		return "/" + concatPath(currentDirectory);
	}
	
	private String concatPath(String[] path)
	{
		return concatPath(Arrays.asList(path));
	}
	
	private String concatPath(List<String> path)
	{
		if (path == null || path.size() == 0)
			return "";
		
		StringBuilder res = new StringBuilder();
		boolean first = true;
		for (String p : path)
		{
			if (!first)
				res.append("/");
			else
				first = false;
			
			res.append(p);
		}
		
		return res.toString();
	}

}
