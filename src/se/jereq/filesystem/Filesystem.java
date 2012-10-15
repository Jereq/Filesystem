package se.jereq.filesystem;

import java.util.ArrayList;
import java.util.List;

import se.jereq.filesystem.INode.Type;

public class Filesystem
{
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
		m_BlockDevice.writeBlock(0, root.getBlock());
		dumpINode(root);
		
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
	
	private INode findChildNode(INode current, String next)
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
				absolutePath.remove(absolutePath.size() - 1);
			else
				absolutePath.add(s);
		}
		
		return absolutePath.toArray(new String[0]);
	}
	
	private INode findINode(String[] path)
	{
		String[] absolutePath = toAbsolute(path);
		
		INode node = new INode(m_BlockDevice.readBlock(0));
		for (String s : absolutePath)
		{
			node = findChildNode(node, s);
			
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
		
		StringBuilder res = new StringBuilder("Listing directory /");
		for (String p : toAbsolute(p_asPath))
			res.append(p).append("/");
		
		res.append("\n\n");
		
		int i = 0;
		int currChild = dir.getChild(i++);
		while (currChild != -1)
		{
			INode child = new INode(m_BlockDevice.readBlock(currChild));
			
			String name = child.getName();
			
			res.append(name).append(new String(new char[20 - name.length()]).replace('\0', ' '));
			res.append(child.getType()).append("\n");
			
			currChild = dir.getChild(i++);
		}
		
		if (i == 1)
		{
			res.append("Empty directory");
		}
		
		return res.toString();
	}

	public String create(String[] p_asPath,byte[] p_abContents)
	{
		System.out.print("Creating file ");
		dumpArray(p_asPath);
		System.out.print("");
		return new String("");
	}

	public String cat(String[] p_asPath)
	{
		System.out.print("Dumping contents of file ");
		dumpArray(p_asPath);
		System.out.print("");
		return new String("");
	}

	public String save(String p_sPath)
	{
		System.out.print("Saving blockdevice to file "+p_sPath);
		return new String("");
	}

	public String read(String p_sPath)
	{
		System.out.print("Reading file "+p_sPath+" to blockdevice");
		return new String("");
	}

	public String rm(String[] p_asPath)
	{
		System.out.print("Removing file ");
		dumpArray(p_asPath);
		System.out.print("");
		return new String("");
	}

	public String copy(String[] p_asSource,String[] p_asDestination)
	{
		System.out.print("Copying file from ");
		dumpArray(p_asSource);
		System.out.print(" to ");
		dumpArray(p_asDestination);
		System.out.print("");
		return new String("");
	}

	public String append(String[] p_asSource,String[] p_asDestination)
	{
		System.out.print("Appending file ");
		dumpArray(p_asSource);
		System.out.print(" to ");
		dumpArray(p_asDestination);
		System.out.print("");
		return new String("");
	}

	public String rename(String[] p_asSource,String[] p_asDestination)
	{
		System.out.print("Renaming file ");
		dumpArray(p_asSource);
		System.out.print(" to ");
		dumpArray(p_asDestination);
		System.out.print("");
		return new String("");
	}

	public String mkdir(String[] p_asPath)
	{
		System.out.print("Creating directory ");
		dumpArray(p_asPath);
		System.out.print("");
		return new String("");
	}

	public String cd(String[] p_asPath)
	{
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

}
