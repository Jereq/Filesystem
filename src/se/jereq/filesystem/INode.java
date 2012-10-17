package se.jereq.filesystem;

/**
 * A representation of a directory entry stored in a underlying byte array.
 * Changes must be saved externally by storing the underlying block returned by {@link INode#getBlock}.
 */
public class INode
{
	/**
	 * The maximum number of bytes allowed in filenames.
	 */
	public static final int MAX_FILENAME_LENGTH = 17;
	
	private static final int TYPE_OFFSET = MAX_FILENAME_LENGTH;
	private static final int SIZE_OFFSET = TYPE_OFFSET + 1;
	private static final int CHILDREN_OFFSET = SIZE_OFFSET + 4;
	
	/**
	 * The number of children in each node (files/directories in directories, blocks in files).
	 */
	public static final int NUM_CHILDREN = (BlockDevice.BLOCK_SIZE - CHILDREN_OFFSET) / 2;
	
	private byte[] block;
	
	/**
	 * Different INode types.
	 */
	public enum Type
	{
		/**
		 * Simple file, containing references to a number of blocks.
		 */
		File,
		/**
		 * Directory entry, containing references to other INodes.
		 */
		Directory,
		/**
		 * Error code, normally never used.
		 */
		Unknown
	}

	/**
	 * constructor. Creates an <code>INode</code> for an existing block.
	 * 
	 * @param data byte array of the size specified by the {@link BlockDevice}.
	 * @throws IllegalArgumentException Exception thrown if <code>data</code> is null or of incorrect size.
	 */
	public INode(byte[] data)
	{
		if (data == null || data.length != BlockDevice.BLOCK_SIZE)
			throw new IllegalArgumentException("Only standard sized blocks supported");
		
		block = data;
	}
	
	/**
	 * constructor. Creates a new <code>INode</code> without existing block. 
	 * 
	 * @param name the filename for this <code>INode</code>. Maximum length is given by {@link INode#MAX_FILENAME_LENGTH}.
	 * @param nodeType the type of node to create.
	 */
	public INode(String name, Type nodeType)
	{
		block = new byte[BlockDevice.BLOCK_SIZE];
		setName(name);
		setType(nodeType);
		
		for (int i = CHILDREN_OFFSET; i < block.length; i += 2)
		{
			putShort(i, (short) -1);
		}
	}
	
	/**
	 * Get the block of data represented by this <code>INode</code>.
	 * 
	 * @return Byte array of the size defined by {@link BlockDevice}. If the returned array is modified,
	 * there is no guarantee that this <code>INode</code> remain valid. 
	 */
	public byte[] getBlock()
	{
		return block;
	}

	/**
	 * Get the name of <code>INode</code> stored in the underlying block.
	 * 
	 * @return The name of the node.
	 */
	public String getName()
	{
		int charCount = 0;
		for (; charCount < MAX_FILENAME_LENGTH; ++charCount)
		{
			if (block[charCount] == '\0')
				break;
		}
		return new String(block, 0, charCount);
	}
	
	/**
	 * Set a new name for the node.
	 * 
	 * @param name A <code>String</code> containing the new name,
	 * no longer than <code>MAX_FILENAME_LENGTH</code> bytes and not empty.
	 * 
	 * @throws IllegalArgumentException Throws an exception if the name is to long or empty.
	 */
	public void setName(String name)
	{
		if (name.length() > MAX_FILENAME_LENGTH)
			throw new IllegalArgumentException("Filename too long");
		if (name.isEmpty())
			throw new IllegalArgumentException("Filename can not be empty");
		
		byte[] bName = name.getBytes();
		
		for (int i = 0; i < bName.length; ++i)
		{
			block[i] = bName[i];
		}
		
		for (int i = bName.length; i < MAX_FILENAME_LENGTH; ++i)
		{
			block[i] = '\0';
		}
	}

	/**
	 * Get the type of the <code>INode</code> stored in the underlying block.
	 * 
	 * @return The type of the node. <code>Unknown</code> if the type is not recognized.
	 */
	public Type getType()
	{
		switch (block[TYPE_OFFSET])
		{
		case 1:
			return Type.File;

		case 2:
			return Type.Directory;

		default:
			return Type.Unknown;
		}
	}
	
	private void setType(Type type)
	{
		switch (type)
		{
		case File:
			block[TYPE_OFFSET] = 1;
			break;
			
		case Directory:
			block[TYPE_OFFSET] = 2;
			break;
			
		default:
			return;
		}
	}
	
	/**
	 * Get the size of the <code>INode</code>'s children. Meaning depends on node type.
	 * 
	 * @return The size of the children, meaning depending on the node type.
	 */
	public int getSize()
	{
		return getInt(SIZE_OFFSET);
	}
	
	/**
	 * Set a new size for the <code>INode</code>. Meaning depends on the node type.
	 * 
	 * @param size the new size, meaning depending on the node type.
	 */
	public void setSize(int size)
	{
		putInt(SIZE_OFFSET, size);
	}
	
	private int toIndex(int num)
	{
		return CHILDREN_OFFSET + 2 * num;
	}
	
	private short getShort(int index)
	{
		return (short) ((block[index] & 0xff) << 8 |
				(block[index + 1] & 0xff));
	}
	
	private void putShort(int index, short val)
	{
		block[index] = (byte) (val >>> 8);
		block[index + 1] = (byte) val;
	}
	
	private int getInt(int index)
	{
		return (block[index] & 0xff) << 24 |
				(block[index + 1] & 0xff) << 16 |
				(block[index + 2] & 0xff) << 8 |
				(block[index + 3] & 0xff);
	}
	
	private void putInt(int index, int val)
	{
		block[index + 0] = (byte) (val >>> 24);
		block[index + 1] = (byte) (val >>> 16);
		block[index + 2] = (byte) (val >>> 8);
		block[index + 3] = (byte) val;
	}

	/**
	 * Get the node's child with the given index.
	 * 
	 * @param num the index of the requested child.
	 * Must be in the range [0, <code>NUM_CHILDREN</code>).
	 * 
	 * @return The child value with the given index,
	 * with -1 representing an empty child.
	 * Return -1 if the index is invalid. 
	 */
	public short getChild(int num)
	{
		if (num < 0 || num >= NUM_CHILDREN)
			return -1;

		int start = toIndex(num);
		return getShort(start);
	}
	
	/**
	 * Add another child to the node.
	 * 
	 * @param val the child value to be added.
	 * @throws RuntimeException Thrown if there is no room for for the new child value.
	 */
	public void addChild(short val)
	{
		for (int i = 0; i < NUM_CHILDREN; ++i)
		{
			short currentVal = getChild(i);
			if (currentVal == -1)
			{
				putShort(toIndex(i), val);
				return;
			}
		}
		
		throw new RuntimeException("Max size reached");
	}
	
	/**
	 * Remove a child by index and shift any following children to remove the empty space.
	 * 
	 * @param num the index of the child to remove.
	 */
	public void removeChild(int num)
	{
		if (num < 0 || num >= NUM_CHILDREN)
			return;
		
		int curInd = toIndex(num);
		for (; curInd < block.length - 3; curInd += 2)
		{
			if (getShort(curInd + 2) == -1)
				break;
			
			block[curInd] = block[curInd + 2];
			block[curInd + 1] = block[curInd + 3];
		}
		
		putShort(curInd, (short) -1);
	}
	
	/**
	 * Remove a child by value if it exists and shift any following children to
	 * remove the empty space. Only one child will be removed even if duplicates exists.
	 * 
	 * @param val the value of the child to remove.
	 */
	public void removeChildByVal(short val)
	{
		int curInd = 0;
		short curVal = getChild(0);
		
		while (curVal != -1)
		{
			if (curVal == val)
			{
				removeChild(curInd);
				return;
			}
			
			curVal = getChild(++curInd);
		}
	}
}
