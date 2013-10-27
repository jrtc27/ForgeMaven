package com.jrtc27.mojo.forgemaven;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;

@Mojo(name = "compile", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.COMPILE)
public class CompileMojo extends AbstractMojo {
	@Component
	private MavenSession session;

	@Component
	private MavenProject project;

	@Component
	private MojoExecution mojo;

	@Component
	private Settings settings;

	@Parameter(property = "relativeMCPPath", defaultValue = "mcp/")
	private String relativeMCPPath;

	@Parameter(property = "compileArgs")
	private String[] compileArgs;

	@Parameter(property = "reobfuscateArgs")
	private String[] reobfuscateArgs;

    @Parameter(property = "libDirectory", defaultValue = "${basedir}/lib/")
    private File libDirectory;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		FileUtils.log = getLog();
		File targetDirectory = new File(project.getBuild().getDirectory()).getAbsoluteFile();
		File forgeDirectory = new File(targetDirectory, "forge");
		File mcpDirectory = new File(forgeDirectory, relativeMCPPath).getAbsoluteFile();
		List<String> command;
		ProcessBuilder processBuilder;

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

		getLog().info("Running recompile.py");
		command = Arrays.asList(ProcessUtils.getPythonProgramName(mcpDirectory), "runtime/recompile.py");
		Collections.addAll(command, compileArgs);
		processBuilder = new ProcessBuilder(command);
		processBuilder.directory(mcpDirectory);
		Process process;
		try {
			process = processBuilder.start();
			ProcessUtils.redirect(process, getLog());
			int code = process.waitFor();
			if (code != 0) throw new RuntimeException("Non-zero exit code " + code);
		} catch (Exception e) {
			throw new MojoExecutionException("Failed to run recompile script", e);
		}

		getLog().info("Running reobfuscate.py");
		command = Arrays.asList(ProcessUtils.getPythonProgramName(mcpDirectory), "runtime/reobfuscate.py");
		Collections.addAll(command, reobfuscateArgs);
		processBuilder = new ProcessBuilder(command);
		processBuilder.directory(mcpDirectory);
		try {
			process = processBuilder.start();
			ProcessUtils.redirect(process, getLog());
			int code = process.waitFor();
			if (code != 0) throw new RuntimeException("Non-zero exit code " + code);
		} catch (Exception e) {
			throw new MojoExecutionException("Failed to run reobfuscate script", e);
		}

		File reobfDirectory = new File(mcpDirectory, "reobf" + File.separator + "minecraft").getAbsoluteFile();
		File outputDirectory = new File(project.getBuild().getOutputDirectory()).getAbsoluteFile();
		getLog().info("Copying compiled output to target directory");
		try {
			FileUtils.copyContents(reobfDirectory, outputDirectory, true);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to copy reobfuscated classes", e);
		}
	}
}
