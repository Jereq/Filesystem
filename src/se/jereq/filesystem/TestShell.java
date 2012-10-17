package se.jereq.filesystem;

/**
 * Start-class for the program.
 * Sets up and runs the systems.
 */
public class TestShell
{
	/**
	 * main.
	 * 
	 * @param args command-line arguments, ignored
	 */
	public static void main(String[] args)
	{
		MemoryBlockDevice BlockTest = new MemoryBlockDevice();
		Filesystem FS = new Filesystem(BlockTest);
		Shell Bash = new Shell(FS, null);	// Standard input
		
		Bash.start();
	}
}
