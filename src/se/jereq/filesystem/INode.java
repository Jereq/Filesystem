package se.jereq.filesystem;
public class INode
{
	public static final int MAX_FILENAME_LENGTH = 17;
	private static final int TYPE_OFFSET = MAX_FILENAME_LENGTH;
	private static final int SIZE_OFFSET = TYPE_OFFSET + 1;
	private static final int CHILDREN_OFFSET = SIZE_OFFSET + 4;
	public static final int NUM_CHILDREN = (BlockDevice.BLOCK_SIZE - CHILDREN_OFFSET) / 2;
	
	private byte[] block;
	
	public enum Type
	{
		File, Directory, Unknown
	}

	public INode(byte[] data)
	{
		if (data.length != BlockDevice.BLOCK_SIZE)
			throw new IllegalArgumentException("Only standard sized blocks supported");
		
		block = data;
	}
	
	public INode(String name, Type type)
	{
		block = new byte[BlockDevice.BLOCK_SIZE];
		setName(name);
		setType(type);
		
		for (int i = CHILDREN_OFFSET; i < block.length; i += 2)
		{
			putShort(i, (short) -1);
		}
	}
	
	public byte[] getBlock()
	{
		return block;
	}

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
	
	public int getSize()
	{
		return getInt(SIZE_OFFSET);
	}
	
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

	public short getChild(int num)
	{
		if (num < 0 || num >= NUM_CHILDREN)
			return -1;

		int start = toIndex(num);
		return getShort(start);
	}
	
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
