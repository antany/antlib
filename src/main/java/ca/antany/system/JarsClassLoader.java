package ca.antany.system;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class JarsClassLoader {

	static final String INTERNAL_URL_PROTOCOL_WITH_COLON = "antlib:";
	static final String INTERNAL_URL_PROTOCOL = "antlib";
	static final String CURRENT_DIR = "./";
	static final String PATH_SEPARATOR = "/";
	static final String UTF8_ENCODING = "UTF-8";
	static final String RUNTIME = "#runtime";

	public static void main(String[] args) throws Exception {
		Manifest manifest = getManifest();
		if (manifest == null) {
			System.err.println("No manifest file found");
			System.exit(1);
		}

		ClassLoader cl = Thread.currentThread().getContextClassLoader();

		URL.setURLStreamHandlerFactory(new JarURLStreamHandlerFactory(cl));

		URL[] urls = null;
		String mainClass = manifest.getMainAttributes().getValue("app-main-class");
		String jars = manifest.getMainAttributes().getValue("inside-jars");
		if (jars != null && !jars.trim().equals("")) {
			String[] jarArray = jars.split(" ");
			urls = new URL[jarArray.length];
			for (int i = 0; i < jarArray.length; i++) {
				urls[i] = new URL("antlib:" + jarArray[i]);
			}
		}

		ClassLoader classLoader = new URLClassLoader(urls, getParentClassLoader());
		Thread.currentThread().setContextClassLoader(classLoader);
		@SuppressWarnings("rawtypes")
		Class c = Class.forName(mainClass, true, classLoader);
		
		@SuppressWarnings("unchecked")
		Method main = c.getMethod("main", new Class[] { args.getClass() });
		main.invoke((Object) null, new Object[] { args });
	}

	private static Manifest getManifest() throws IOException {
		Manifest manifest = null;
		Enumeration<URL> classes = Thread.currentThread().getContextClassLoader().getResources(JarFile.MANIFEST_NAME);
		if (classes.hasMoreElements()) {
			URL manifestURL = classes.nextElement();
			InputStream is = manifestURL.openStream();
			manifest = new Manifest(is);
		}

		return manifest;
	}

	private static ClassLoader getParentClassLoader() throws InvocationTargetException, IllegalAccessException {
		try {
			Method platformClassLoader = ClassLoader.class.getMethod("getPlatformClassLoader", (Class[]) null); //$NON-NLS-1$
			return (ClassLoader) platformClassLoader.invoke(null, (Object[]) null);
		} catch (NoSuchMethodException e) {
			return null;
		}
	}
}

class JarURLStreamHandlerFactory implements URLStreamHandlerFactory {

	private ClassLoader classLoader;
	private URLStreamHandlerFactory chainFac;

	public JarURLStreamHandlerFactory(ClassLoader cl) {
		this.classLoader = cl;
	}

	public URLStreamHandler createURLStreamHandler(String protocol) {
		if (JarsClassLoader.INTERNAL_URL_PROTOCOL.equals(protocol))
			return new JarURLStreamHandler(classLoader);
		if (chainFac != null)
			return chainFac.createURLStreamHandler(protocol);
		return null;
	}

	public void setURLStreamHandlerFactory(URLStreamHandlerFactory fac) {
		chainFac = fac;
	}

}

class JarURLStreamHandler extends java.net.URLStreamHandler {

	private ClassLoader classLoader;

	public JarURLStreamHandler(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	protected java.net.URLConnection openConnection(URL u) throws IOException {
		return new JarURLConnection(u, classLoader);
	}

	protected void parseURL(URL url, String spec, int start, int limit) {
		String file;
		if (spec.startsWith(JarsClassLoader.INTERNAL_URL_PROTOCOL_WITH_COLON))
			file = spec.substring(7);
		else if (url.getFile().equals(JarsClassLoader.CURRENT_DIR))
			file = spec;
		else if (url.getFile().endsWith(JarsClassLoader.PATH_SEPARATOR))
			file = url.getFile() + spec;
		else if (JarsClassLoader.RUNTIME.equals(spec))
			file = url.getFile();
		else
			file = spec;
		setURL(url, JarsClassLoader.INTERNAL_URL_PROTOCOL, "", -1, null, null, file, null, null); //$NON-NLS-1$
	}

}

class JarURLConnection extends URLConnection {

	private ClassLoader classLoader;

	public JarURLConnection(URL url, ClassLoader classLoader) {
		super(url);
		this.classLoader = classLoader;
	}

	public void connect() throws IOException {
	}

	public InputStream getInputStream() throws IOException {
		String file = URLDecoder.decode(url.getFile(), JarsClassLoader.UTF8_ENCODING);
		InputStream result = classLoader.getResourceAsStream(file);
		if (result == null) {
			throw new MalformedURLException("Could not open InputStream for URL '" + url + "'"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return result;
	}
}
