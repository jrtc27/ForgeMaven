package com.jrtc27.mojo.forgemaven;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;

@Mojo(name = "initialize", defaultPhase = LifecyclePhase.INITIALIZE)
public class InitializeMojo extends AbstractMojo {
	@Component
	private MavenSession session;

	@Component
	private MavenProject project;

	@Component
	private MojoExecution mojo;

	@Component
	private Settings settings;

	@Parameter(property = "forgeWorkspaceURL", required = true)
	private URL forgeWorkspaceURL;

	@Parameter(property = "libDirectory", defaultValue = "${basedir}/lib/")
	private File libDirectory;

	@Parameter(property = "relativeMCPPath", defaultValue = "mcp/")
	private String relativeMCPPath;

	private void extractZip(File zipFile, File outputFolder) throws IOException {
		byte[] buffer = new byte[1024];

		if (!outputFolder.exists()) {
			outputFolder.mkdirs();
		}

		ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFile));
		ZipEntry zipEntry = zipInputStream.getNextEntry();

		while (zipEntry != null) {
			String fileName = zipEntry.getName();
			File newFile = new File(outputFolder, fileName);
			//getLog().info("Extracting to " + newFile.getAbsolutePath());

			if (zipEntry.isDirectory()) {
				newFile.mkdirs();
			} else {
				new File(newFile.getParent()).mkdirs();
				FileOutputStream fileOutputStream = new FileOutputStream(newFile);

				int len;
				while ((len = zipInputStream.read(buffer)) != -1) {
					fileOutputStream.write(buffer, 0, len);
				}

				fileOutputStream.close();
			}
			zipEntry = zipInputStream.getNextEntry();
		}

		zipInputStream.closeEntry();
		zipInputStream.close();
	}

	private void deleteRecursively(File file) throws IOException {
		if (!file.exists()) return;
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			if (files != null) {
				for (File f : files) {
					deleteRecursively(f);
				}
			}
		}
		if (!file.delete()) throw new IOException("Failed to delete file " + file.getAbsolutePath());
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		FileUtils.log = getLog();

		File targetDirectory = new File(project.getBuild().getDirectory()).getAbsoluteFile();
		targetDirectory.mkdirs();
		File zipDestination = new File(targetDirectory, "forge.zip");
		File forgeDestination = new File(targetDirectory, "forge");
		File mcpDirectory = new File(forgeDestination, relativeMCPPath).getAbsoluteFile();
		try {
			deleteRecursively(zipDestination);
			deleteRecursively(forgeDestination);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to clean Forge workspace", e);
		}

		getLog().info("Downloading " + forgeWorkspaceURL.toExternalForm());
		try {
			URLConnection connection = forgeWorkspaceURL.openConnection();
			connection.addRequestProperty("User-Agent", "Mozilla/4.0");
			BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
			OutputStream out = new FileOutputStream(zipDestination);
			byte data[] = new byte[1024];
			int count;
			while ((count = in.read(data, 0, 1024)) != -1) {
				out.write(data, 0, count);
			}
			in.close();
		} catch (Exception e) {
			throw new MojoExecutionException("Failed to download Forge workspace", e);
		}

		getLog().info("Extracting " + zipDestination.getAbsolutePath() + " to " + forgeDestination.getAbsolutePath());
		try {
			extractZip(zipDestination, forgeDestination);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to extract Forge workspace", e);
		}

		getLog().info("Running updatemd5.py");
		List<String> command = Arrays.asList("python", "runtime/updatemd5.py", "-f");
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.directory(mcpDirectory);
		try {
			Process process = processBuilder.start();
			ProcessUtils.redirect(process, getLog());
			int code = process.waitFor();
			if (code != 0) throw new RuntimeException("Non-zero exit code " + code);
		} catch (Exception e) {
			throw new MojoExecutionException("Failed to run updatemd5 script", e);
		}

		getLog().info("Copying source files into Forge workspace");
		try {
			FileUtils.copyContents(new File(project.getBuild().getSourceDirectory()), new File(mcpDirectory, "src" + File.separator + "minecraft"), false);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to copy relevant source files into the Forge workspace", e);
		}

		if (libDirectory != null) {
			getLog().info("Copying library files into Forge workspace");
			try {
				FileUtils.copyContents(libDirectory, new File(mcpDirectory, "lib"), false);
			} catch (IOException e) {
				throw new MojoExecutionException("Failed to copy relevant source files into the Forge workspace", e);
			}
		} else {
			getLog().info("No library files to copy");
		}
	}
}
