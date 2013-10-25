package com.jrtc27.mojo.forgemaven;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.maven.plugin.logging.Log;

public class ProcessUtils {
	private static class StreamGobbler extends Thread {
		private final InputStream is;
		private final Log log;

		private StreamGobbler(InputStream is, Log log) {
			this.is = is;
			this.log = log;
		}

		@Override
		public void run() {
			try {
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line;
				while ((line = br.readLine()) != null) log.info(line);
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
	}

	public static void redirect(Process process, Log log) {
		new StreamGobbler(process.getInputStream(), log).start();
		new StreamGobbler(process.getErrorStream(), log).start();
	}

	private static Boolean isWindows = null;

	private static boolean isWindows() {
		if (isWindows == null) {
			isWindows = System.getProperty("os.name").startsWith("Windows");
		}
		return isWindows;
	}

	/**
	 * Assuming running in MCP directory
	 */
	public static String getPythonProgramName() {
		if (isWindows()) {
			return "runtime" + File.separator + "bin" + File.separator + "python" + File.separator + "python_mcp.exe";
		} else {
			return "python";
		}
	}
}
