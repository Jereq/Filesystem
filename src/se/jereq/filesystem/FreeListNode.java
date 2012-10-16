package se.jereq.filesystem;

public class FreeListNode {

	private static final int FREE_LIST_START = 2;
	
	private byte[] block;
	
	public FreeListNode()
	{
		block = new byte[BlockDevice.BLOCK_SIZE];
		block[FREE_LIST_START] = (byte) 0xc0;
		setFirstFree(Filesystem.BLOCK_START);
	}
	
	public FreeListNode(byte[] data)
	{
		if (data.length != BlockDevice.BLOCK_SIZE)
			throw new IllegalArgumentException("Only standard sized blocks supported");
		
		if ((data[FREE_LIST_START] & 0xc0) != 0xc0)
			throw new IllegalArgumentException("Invalid data");
		
		block = data;
	}
	
	private void setFirstFree(short free)
	{
		block[0] = (byte) (free >>> 8);
		block[1] = (byte) (free & 0xff);
	}
	
	/**
	 * This is only a hint as to where to start searching for the next free block.
	 * Earlier blocks are guaranteed to be taken, but the actual first free block
	 * may be quite a bit later.
	 * 
	 * @return A hint as to where to start searching for a free block.
	 */
	private short getFirstFree()
	{
		return (short) (block[0] << 8 | block[1]);
	}
	
	public short getNewBlock()
	{
		int i = getFirstFree() / 8;
		
		for (; i < BlockDevice.BLOCK_SIZE - FREE_LIST_START; ++i)
		{
			byte freeByte = block[FREE_LIST_START + i];
			
			if (freeByte != -1)
			{
				int firstFreeInByte = Integer.numberOfLeadingZeros(~(freeByte << 24));
				block[FREE_LIST_START + i] |= 1 << (7 - firstFreeInByte);
				
				short res = (short) (i * 8 + firstFreeInByte);
				setFirstFree(res);
				
				return res;
			}
		}
		
		return -1;
	}
	
	public void freeBlock(short num)
	{
		int byteNum = num / 8;
		int bitInByte = num % 8;
		
		block[FREE_LIST_START + byteNum] &= ~(0x80 >>> bitInByte);
		
		if (getFirstFree() > num)
		{
			setFirstFree(num);
		}
	}

	public byte[] getBlock()
	{
		return block;
	}
}
