package se.jereq.filesystem;
public abstract class BlockDevice
{
	public static final int BLOCK_SIZE = 512;
	public static final int BLOCK_COUNT = 250;
	
	/**
	 * Write a 512 byte block to "disk"
	 * 
	 * @param p_nBlockNr The block to be overwritten, in the range [0, 250).
	 * @param p_abContents The byte array to be written, with a length of 512.
	 * @return -1 if the block number is invalid and -2 if the byte array is an invalid length. Otherwise 1.
	 */
	public abstract int writeBlock(int p_nBlockNr,byte[] p_abContents);
	
	/**
	 * Read a 512 byte block from "disk"
	 * 
	 * @param p_nBlockNr The block to be read, in the range [0, 250).
	 * @return An empty array if the block number is invalid. Otherwise a copy of the block as an array of 512 byte.
	 */
	public abstract byte[] readBlock(int p_nBlockNr);

}
