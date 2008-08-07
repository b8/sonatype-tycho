package org.codehaus.tycho.surefire.osgibooter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.apache.maven.surefire.Surefire;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;

public class OsgiSurefireBooter {

	public static int run(String[] args) throws Exception {
		
		Properties p = loadProperties(getTestProperties(args));
		
		String plugin = p.getProperty("testpluginname");
		File testDir = new File(p.getProperty("testclassesdirectory"));
		File reportsDir = new File(p.getProperty("reportsdirectory"));
		
		ArrayList<String> includes = getIncludesExcludes(p.getProperty("includes"));
		ArrayList<String> excludes = getIncludesExcludes(p.getProperty("excludes"));

		ClassLoader testClassLoader = getBundleClassLoader(plugin);
		ClassLoader surefireClassLoader = Surefire.class.getClassLoader();

		Surefire surefire = new Surefire();

		List reports = new ArrayList();
		reports.add(new Object[] {
			"org.apache.maven.surefire.report.BriefConsoleReporter",
			new Object[] {
				Boolean.TRUE /*trimStackTrace*/
			}
		});
		reports.add(new Object[] {
			"org.apache.maven.surefire.report.XMLReporter",
			new Object[] {
				reportsDir,
				Boolean.TRUE /*trimStackTrace*/
			}
		});

		List tests = new ArrayList();
		tests.add(new Object[] {
			"org.apache.maven.surefire.junit.JUnitDirectoryTestSuite",
			new Object[] {
				testDir,
				includes,
				excludes
			}
		});

		return surefire.run(reports, tests, surefireClassLoader, testClassLoader, true /*failIfNoTests*/);
	}

	private static File getTestProperties(String[] args) throws CoreException {
		String arg = null; 
		for (int i = 0; i < args.length; i++) {
			if ("-testproperties".equals(args[i].toLowerCase())) {
				arg = args[i+1];
				break;
			}
		}
		if (arg != null) {
			File file = new File(arg);
			if (file.canRead()) {
				return file;
			}
		}
		throw new CoreException(new Status(IStatus.ERROR, Activator.PLUGIN_ID, 0, "-testproperties command line parameter is not specified or does not point to an accessible file", null));
	}

	private static ArrayList<String> getIncludesExcludes(String string) {
		ArrayList<String> list = new ArrayList<String>();
		list.addAll(Arrays.asList(string.split(",")));
		return list;
	}

	private static Properties loadProperties(File file) throws IOException {
		Properties p = new Properties();
		BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
		try {
			p.load(in);
		} finally {
			in.close();
		}
		return p;
	}

	private static ClassLoader getBundleClassLoader(String symbolicName) {
		Bundle bundle = Activator.getBundle(symbolicName);
		return new BundleClassLoader(bundle);
	}

	private static class BundleClassLoader extends ClassLoader {
		private Bundle bundle;

		public BundleClassLoader(Bundle target) {
			this.bundle = target;
		}

		protected Class findClass(String name) throws ClassNotFoundException {
			return bundle.loadClass(name);
		}

		protected URL findResource(String name) {
			return bundle.getResource(name);
		}

		protected Enumeration findResources(String name) throws IOException {
			return bundle.getResources(name);
		}
	}
}