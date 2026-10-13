package us.bringardner.fsh.ide;

/**
 * Tests that use a temporary home folder (JUnit's @TempDir) delete it when the class is done,
 * and Windows will not delete a file that is open.
 * <p>
 * Jazzy, the spelling engine under RSyntaxTextArea's spell checker, opens the user's word list
 * with a FileReader it never closes (SpellDictionaryHashMap(File), used by
 * SpellingParser.setUserDictionary), so the file stays open until the reader is collected.
 * {@link #release()} collects it.
 */
final class TestHandles {

	private TestHandles() {
	}

	/** Lets go of files that a library opened and did not close. */
	static void release() throws InterruptedException {
		for(int i=0; i < 3; i++) {
			System.gc();
			Thread.sleep(100);
		}
	}
}
