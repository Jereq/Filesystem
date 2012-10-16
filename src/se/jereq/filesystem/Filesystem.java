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
		INode root = new INode("", INode.Type.Directory);
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
		
		FreeListNode free = getFreeList();
		
		short fileNum = free.getNewBlock();
		INode fileNode = new INode(filename, Type.File);
		
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
		
		System.out.print("Removing file ");
		dumpArray(p_asPath);
		System.out.print("");
		return new String("");
	}

	public String copy(String[] p_asSource,String[] p_asDestination)
	{
		if (currentNode < 0)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";
		
		System.out.print("Copying file from ");
		dumpArray(p_asSource);
		System.out.print(" to ");
		dumpArray(p_asDestination);
		System.out.print("");
		return new String("");
	}

	public String append(String[] p_asSource,String[] p_asDestination)
	{
		if (currentNode < 0)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";
		
		System.out.print("Appending file ");
		dumpArray(p_asSource);
		System.out.print(" to ");
		dumpArray(p_asDestination);
		System.out.print("");
		return new String("");
	}

	public String rename(String[] p_asSource,String[] p_asDestination)
	{
		if (currentNode < 0)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";
		
		System.out.print("Renaming file ");
		dumpArray(p_asSource);
		System.out.print(" to ");
		dumpArray(p_asDestination);
		System.out.print("");
		return new String("");
	}

	public String mkdir(String[] p_asPath)
	{
		if (currentNode < 0)
			return "Invalid filesystem. Use format or read to prepare the filesystem before use.";
		
		System.out.print("Creating directory ");
		dumpArray(p_asPath);
		System.out.print("");
		return new String("");
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
