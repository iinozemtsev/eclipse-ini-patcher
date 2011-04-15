package com.xored.tycho.patcher;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;

/**
 * Goal which modifies eclipse.ini file for Mac OS X platform.
 * 
 * @goal patch-ini-file
 * @phase package
 */
public class MyMojo extends AbstractMojo {
	private static final String ERROR_NO_INI_FILE = "Product %s does not contain eclipse.ini for Mac OS X, skipped";
	private static final String ERROR_NO_LAUNCHER = "Product %s does not contain equinox launcher, skipped";
	private static final String ERROR_ALREADY_PATCHED = "Product %s already contains startup arg in eclipse.ini, skipped";
	/**
	 * Location of the file.
	 * 
	 * @parameter expression="${project.build.directory}"
	 * @required
	 */
	private File outputDirectory;

	public void execute() throws MojoExecutionException {
		File productsDir = new File(outputDirectory, "products");
		for(File productFile  : productsDir.listFiles()) {
			if(productFile.isDirectory()) {
				patchProduct(productFile);
			}
		}
	}

	private void patchProduct(File productDir) {
		// Ideally we should use tycho project utils here to get
		// destination directory of Mac OS X product, but for now just
		// assume default location

		File macDir = new File(productDir, "macosx/cocoa/x86_64");
		if(!macDir.exists() || !macDir.isDirectory()) {
			return;
		}
		
		while(macDir.list().length == 1) {
			macDir = macDir.listFiles()[0];
		}
		File launcherFile = findLauncher(macDir);
		if (launcherFile == null || !launcherFile.exists()) {
			getLog().warn(String.format(ERROR_NO_LAUNCHER, productDir.getName()));
			return;
		}
		File launcherLib = findLauncherLib(macDir);
		if (launcherFile == null || !launcherFile.exists()) {
			getLog().warn(String.format(ERROR_NO_LAUNCHER, productDir.getName()));
			return;
		}


		for (File app : getAppDirs(macDir)) {
			File iniDir = new File(new File(app, "Contents"), "MacOS");
			if (!iniDir.exists() || !iniDir.isDirectory()) {
				continue;
			}
			for (File iniFile : getIniFiles(iniDir)) {
				patchIniFile(productDir.getName(), launcherFile, launcherLib, iniFile);
			}
		}

	}

	private void patchIniFile(String productDirName, File launcherFile,
			File launcherLib, File iniFile) {
		if (!iniFile.exists()) {
			getLog().warn(String.format(ERROR_NO_INI_FILE, productDirName));
			return;
		}

		try {
			String contents = FileUtils.fileRead(iniFile);
			StringBuilder patched = new StringBuilder();
			if (contents.contains(STARTUP_ARG)) {
				getLog().warn(String.format(ERROR_ALREADY_PATCHED, productDirName));
			} else {
				patched.append(STARTUP_ARG).append("\n");
				patched.append(
						String.format("../../../plugins/%s",
								launcherFile.getName())).append("\n");
			}
			if (contents.contains(LAUNCHER_LIB_ARG)) {
				getLog().warn(String.format(ERROR_ALREADY_PATCHED, productDirName));
			} else {
				patched.append(LAUNCHER_LIB_ARG).append("\n");
				patched.append(
						String.format("../../../plugins/%s",
								launcherLib.getName())).append("\n");
			}
			patched.append(contents);
			FileUtils.fileWrite(iniFile, patched.toString());

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private List<File> getIniFiles(File parent) {
		List<File> result = new ArrayList<File>();
		for (File child : parent.listFiles()) {
			if (child.isFile() && child.getName().endsWith(INI_EXT)) {
				result.add(child);
			}
		}
		return result;
	}

	private List<File> getAppDirs(File parent) {
		List<File> result = new ArrayList<File>();
		for (File child : parent.listFiles()) {
			if (child.isDirectory() && child.getName().endsWith(APP_EXT)) {
				result.add(child);
			}
		}
		return result;
	}

	/**
	 * Searches for equinox launcher jar, returns null if not found
	 * 
	 * @param macDir
	 * @return
	 */
	private File findLauncher(File macDir) {
		File plugins = new File(macDir, "plugins");
		if (!plugins.exists()) {
			return null;
		}

		List<File> candidates = new ArrayList<File>();
		for (File file : plugins.listFiles()) {
			if (file.isFile() && file.getName().startsWith(LAUNCHER_JAR)
					&& file.getName().endsWith(JAR_EXT)) {
				candidates.add(file);
			}
		}
		if (candidates.isEmpty()) {
			return null;
		}

		// here should be honest sort by version, but for now assume that
		// string comparison works fine
		Collections.sort(candidates);
		return candidates.get(candidates.size() - 1);
	}

	private File findLauncherLib(File macDir) {
		File plugins = new File(macDir, "plugins");
		if (!plugins.exists()) {
			return null;
		}

		List<File> candidates = new ArrayList<File>();
		for (File file : plugins.listFiles()) {
			if (file.isDirectory() && file.getName().startsWith(LAUNCHER_LIB_PREFIX)) {
				candidates.add(file);
			}
		}
		if (candidates.isEmpty()) {
			return null;
		}

		// here should be honest sort by version, but for now assume that
		// string comparison works fine
		Collections.sort(candidates);
		return candidates.get(candidates.size() - 1);
	}

	private static final String LAUNCHER_JAR = "org.eclipse.equinox.launcher_";
	private static final String JAR_EXT = ".jar";
	private static final String APP_EXT = ".app";
	private static final String INI_EXT = ".ini";
	private static final String STARTUP_ARG = "-startup";
	private static final String LAUNCHER_LIB_ARG = "--launcher.library";
	private static final String LAUNCHER_LIB_PREFIX = "org.eclipse.equinox.launcher.cocoa.macosx";
}
