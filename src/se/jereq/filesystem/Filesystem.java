package se.jereq.filesystem;

public class Filesystem
{
	private BlockDevice m_BlockDevice;

	private String[] currentDirectory = new String[0];

	public Filesystem(BlockDevice p_BlockDevice)
	{
		m_BlockDevice = p_BlockDevice;
	}

	public String format()
	{
		INode root = new INode("", INode.Type.Directory);
		m_BlockDevice.writeBlock(0, root.getBlock());
		dumpINode(root);
		
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

	public String ls(String[] p_asPath)
	{
		System.out.print("Listing directory ");
		dumpArray(p_asPath);
		System.out.print("");
		return new String("");
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
		StringBuilder res = new StringBuilder("/");
		for (String s : currentDirectory)
		{
			res.append(s).append('/');
		}
		return res.toString();
	}

	private void dumpArray(String[] p_asArray)
	{
		for(int nIndex=0;nIndex<p_asArray.length;nIndex++)
		{
			System.out.print(p_asArray[nIndex]+"=>");
		}
	}

}
