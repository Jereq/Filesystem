package se.jereq.filesystem;

/**
 * Implements {@link BlockDevice} as a memory mapped disk.
 */
public class MemoryBlockDevice extends BlockDevice
{
	private byte[][] m_abContents = new byte[BLOCK_COUNT][BLOCK_SIZE];

	public int writeBlock(int p_nBlockNr,byte[] p_abContents)
	{
		if (p_nBlockNr >= BLOCK_COUNT || p_nBlockNr < 0)
		{
			// Block out-of-range
			return -1;
		}

		if (p_abContents.length != BLOCK_SIZE)
		{
			// Block size out-of-range
			return -2;
		}

		for (int nIndex = 0; nIndex < BLOCK_SIZE; nIndex++)
		{
			m_abContents[p_nBlockNr][nIndex] = p_abContents[nIndex];
		}
		
		return 1;
		
	}

	public byte[] readBlock(int p_nBlockNr)
	{
		if(p_nBlockNr >= BLOCK_COUNT || p_nBlockNr < 0)
		{
			// Block out-of-range
			return new byte[0];
		}

		byte[] abBlock = new byte[BLOCK_SIZE];

		for(int nIndex=0; nIndex < BLOCK_SIZE; nIndex++)
		{
			abBlock[nIndex] = m_abContents[p_nBlockNr][nIndex];
		}

		return abBlock; 
	}
}
