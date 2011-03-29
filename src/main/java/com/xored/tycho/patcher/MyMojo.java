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

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Goal which modifies eclipse.ini file for Mac OS X platform.
 * 
 * @goal patch-ini-file
 * @phase package
 */
public class MyMojo extends AbstractMojo {
	private static final String ERROR_GET_CONFIG = "Can't get tycho-p2-director plugin configuration, execution stopped";
	private static final String ERROR_READ_CONFIG = "Configuration of tycho-p2-director plugin is not understood, execution stopped";
	private static final String ERROR_NO_PRODUCTS = "No products found in tycho-p2-director configuration, execution stopped";

	private static final String ERROR_NO_PRODUCT = "Product %s is not found in target dir, skipped";
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

	/**
	 * @parameter expression="${project}"
	 * */
	private MavenProject project;

	public void execute() throws MojoExecutionException {
		// get products configuration
		Plugin plugin = project
				.getPlugin("org.sonatype.tycho:tycho-p2-director-plugin");
		if (plugin == null) {
			getLog().warn(ERROR_GET_CONFIG);
			return;
		}

		Object config = plugin.getConfiguration();
		if (!(config instanceof Xpp3Dom)) {
			getLog().warn(ERROR_READ_CONFIG);
			return;
		}

		Xpp3Dom dom = (Xpp3Dom) plugin.getConfiguration();
		Xpp3Dom products = dom.getChild("products");
		if (products == null) {
			getLog().warn(ERROR_READ_CONFIG);
			return;
		}

		List<String> productIds = new ArrayList<String>();
		for (Xpp3Dom product : products.getChildren("product")) {
			Xpp3Dom productId = product.getChild("id");
			if (productId != null) {
				productIds.add(productId.getValue());
			}
		}

		if (productIds.isEmpty()) {
			getLog().warn(ERROR_NO_PRODUCTS);
		}

		for (String productId : productIds) {
			patchProduct(productId);
		}
	}

	private void patchProduct(String productId) {
		File dir = new File(new File(outputDirectory, "products"), productId);
		if (!dir.exists() || !dir.isDirectory()) {
			getLog().warn(String.format(ERROR_NO_PRODUCT, productId));
			return;
		}

		// Ideally we should use tycho project utils here to get
		// destination directory of Mac OS X product, but for now just
		// assume default location

		File macDir = new File(dir, "macosx/cocoa/x86_64");
		File iniFile = new File(macDir,
				"Eclipse.app/Contents/MacOS/eclipse.ini");
		File launcherFile = findLauncher(macDir);
		if (!launcherFile.exists()) {
			getLog().warn(String.format(ERROR_NO_LAUNCHER, productId));
			return;
		}
		if (!iniFile.exists()) {
			getLog().warn(String.format(ERROR_NO_INI_FILE, productId));
			return;
		}

		try {
			String contents = FileUtils.fileRead(iniFile);
			if (contents.contains(STARTUP_ARG)) {
				getLog().warn(String.format(ERROR_ALREADY_PATCHED, productId));
				return;
			}
			// prepend contents with location
			StringBuilder patched = new StringBuilder();
			patched.append(STARTUP_ARG).append("\n");
			patched.append(
					String.format("../../../plugins/%s", launcherFile.getName()))
					.append("\n");
			patched.append(contents);
			FileUtils.fileWrite(iniFile, patched.toString());

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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

	private static final String LAUNCHER_JAR = "org.eclipse.equinox.launcher";
	private static final String JAR_EXT = ".jar";
	private static final String STARTUP_ARG = "-startup";
}
