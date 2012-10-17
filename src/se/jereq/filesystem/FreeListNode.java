package se.jereq.filesystem;

/**
 * Represents a list of free blocks accessible with the filesystem, using an underlying block.
 * Changes must be saved externally.
 */
public class FreeListNode {

	private static final int FREE_LIST_START = 2;
	
	private byte[] block;
	
	/**
	 * constructor. Creates the default free list block.
	 */
	public FreeListNode()
	{
		block = new byte[BlockDevice.BLOCK_SIZE];
		block[FREE_LIST_START] = (byte) 0xc0;
		setFirstFree(Filesystem.BLOCK_START);
	}
	
	/**
	 * constructor. Creates a <code>FreeListNode</code> for an existing block.
	 * 
	 * @param data byte array of the size specified by the {@link BlockDevice}.
	 * @throws IllegalArgumentException Exception thrown if <code>data</code> is null or of incorrect size.
	 */
	public FreeListNode(byte[] data)
	{
		if (data == null || data.length != BlockDevice.BLOCK_SIZE)
			throw new IllegalArgumentException("Only standard sized blocks supported");
		
		if ((data[FREE_LIST_START] & 0xc0) != 0xc0)
			throw new IllegalArgumentException("Invalid data");
		
		block = data;
	}
	
	private void setFirstFree(short free)
	{
		block[0] = (byte) (free >>> 8);
		block[1] = (byte) (free);
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
		return (short) ((block[0] & 0xff) << 8 |
				block[1] & 0xff);
	}
	
	/**
	 * Get the number of a free block and mark it as taken. May return block numbers not
	 * supported by the filesystem when the filesystem is out of space.
	 * 
	 * @return The number of the taken free block, or -1 if none could be found.
	 */
	public short getNewBlock()
	{
		int i = getFirstFree() / 8;
		
		for (; i < BlockDevice.BLOCK_SIZE - FREE_LIST_START; ++i)
		{
			byte freeByte = block[FREE_LIST_START + i];
			
			if (freeByte != -1)
			{
				int firstFreeInByte = Integer.numberOfLeadingZeros(~((freeByte & 0xff) << 24));
				block[FREE_LIST_START + i] |= 1 << (7 - firstFreeInByte);
				
				short res = (short) (i * 8 + firstFreeInByte);
				setFirstFree(res);
				
				return res;
			}
		}
		
		return -1;
	}
	
	/**
	 * Mark the target block as free. Not checked if it is already free.
	 * 
	 * @param num the number of the block to free. All block numbers supported
	 * by the filesystem should be valid.
	 */
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

	/**
	 * Get the underlying block, usually in order to store it.
	 * 
	 * @return Byte array of the size defined by {@link BlockDevice}. If the returned array is modified,
	 * there is no guarantee that this <code>INode</code> remain valid. 
	 */
	public byte[] getBlock()
	{
		return block;
	}
}
