package com.jrtc27.mojo.forgemaven;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import org.apache.maven.plugin.logging.Log;

public class FileUtils {
	public static Log log;
	protected static void copyContents(File source, File destination, boolean overwrite) throws IOException {
		File[] files = source.listFiles();
		//log.debug("copyContents - from " + source.getAbsolutePath() + " to " + destination.getAbsolutePath());
		if (files == null) {
			log.warn("EMPTY SOURCE DIRECTORY");
			return;
		}
		for (File file : files) {
			File newDestination = new File(destination, file.getName());
			log.info("Copying " + file.getAbsolutePath() + " to " + newDestination.getAbsolutePath());
			if (file.isDirectory()) {
				newDestination.mkdirs();
				copyContents(file, newDestination, overwrite);
			} else {
				if (newDestination.exists() && !overwrite) throw new IOException("File " + newDestination + " already exists!");
				FileChannel sourceChannel = new FileInputStream(file).getChannel();
				FileChannel destChannel = new FileOutputStream(newDestination, false).getChannel();
				destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
			}
		}
	}
}
