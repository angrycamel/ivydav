package org.angrycamel.ivydav;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.util.Message;

import com.googlecode.sardine.Sardine;
import com.googlecode.sardine.SardineFactory;
import com.googlecode.sardine.util.SardineException;

public class DavRepository {

	private final String urlRoot;
	Sardine sardine;
	
	static final String DIRECTORY_CONTENT_TYPE="httpd/unix-directory";

	static class WebserverData {
		long modifiedAt;
		long createdAt;
		long contentLength;
		String contentType;

		public long getModifiedAt() {
			return modifiedAt;
		}
		public long getCreatedAt() {
			return createdAt;
		}
		public long getContentLength() {
			return contentLength;
		}
		public String getContentType() {
			return contentType;
		}
		public boolean equals(Object o) {
			if (o == this) return true;
			else if (o == null || getClass() != o.getClass()) return false;
			WebserverData wd = (WebserverData)o;
			return contentType.equals(wd.contentType) &&
			  contentLength == wd.contentLength &&
			  createdAt == wd.createdAt &&
			  modifiedAt == wd.modifiedAt;
		}
	}

	DavRepository(String root, String user, String pass) {
		this.urlRoot = root.endsWith("/") ? root : root + "/";
		this.sardine = createSardine(user, pass);
	}
	private Sardine createSardine(String user, String pass) {
		try {
			if (user == null) {
				return SardineFactory.begin();
			} else {
				return SardineFactory.begin(user, pass);
			}
		} catch (SardineException ex) {
			throw new RuntimeException("Exception creating WebDAV connector",
					ex);
		}
	}
	// root has been canonicalised to end in /
	// path my have a preceding /
	static String combine(String root, String path, boolean isPath) {
		String slashlessPath = path.startsWith("/") && path.length() > 0 ? path.substring(1) : path;
		String combined = root + slashlessPath;
		if (isPath && !combined.endsWith("/")) {
			return combined + "/";
		}
		return combined;
	}

	String getDavURIAsItem(String uri) {
		return combine(urlRoot, uri, false);
	}

	String getDavURIAsPath(String uri) {
		return combine(urlRoot, uri, true);
	}

	String getDavURI(String uri, boolean treatAsPath) {
		return combine(urlRoot, uri, treatAsPath);
	}

	private String stripRoot(String davURI) {
		if (davURI.startsWith(urlRoot)) {
			String ret = davURI.substring(urlRoot.length());
			return ret;
		}
		throw new IllegalArgumentException(
				"uri returned from directory listing is not for the same root!");
	}

	public static String httpize(String davURI) {
		if (davURI == null) {
			return "";
		}

		if (davURI.startsWith(IvyRepository.PROTOCOL + "://")) {
			davURI = davURI.replaceFirst(IvyRepository.PROTOCOL, "http");
		}

		return davURI;
	}

	public static String unhttpize(String httpURI) {
		if (httpURI == null) {
			return "";
		}

		if (httpURI.startsWith("http://")) {
			httpURI = httpURI.replaceFirst("http", IvyRepository.PROTOCOL);
		}

		return httpURI;
	}

	private List<com.googlecode.sardine.DavResource> getSardineDirectoryResources(
			String uri) throws IOException {
		String httpURI = httpize(getDavURIAsPath(uri));
		return sardine.getResources(httpURI);
	}

	List<String> getDirectoryResources(String uri) throws IOException {
		ArrayList<String> items = new ArrayList<String>();
		List<com.googlecode.sardine.DavResource> sardineResources = getSardineDirectoryResources(uri);
        for (com.googlecode.sardine.DavResource resource : sardineResources) {
        	if (!resource.isCurrentDirectory()) {
        		items.add(stripRoot(unhttpize(resource.getAbsoluteUrl())));
        	}
        }
		return items;
	}
	
	private com.googlecode.sardine.DavResource getSardineResource(String uri)
			throws IOException, SardineException {
		String httpURI = httpize(getDavURIAsItem(uri));
		List<com.googlecode.sardine.DavResource> resources = sardine.getResources(httpURI);
		if (resources == null || resources.isEmpty()) {
			return null;
		} else if (resources.size() > 1) {
			throw new IOException(uri + " cannot be accessed as a single item");
		} else {
			return resources.get(1);
		}
	}

	WebserverData getResourceMetadata(String uri) throws IOException {
		String httpUri = httpize(getDavURIAsItem(uri));
		WebserverData data = null;
		try {
			List<com.googlecode.sardine.DavResource> resources = sardine.getResources(httpUri);
			com.googlecode.sardine.DavResource resource = resources.get(0);
			data = createWebserverContent(resource);
		}
		catch (SardineException ex) {
			if (ex.getStatusCode() == 404) {
				return null;
			}
			else {
				throw ex;
			}
		}
		return data;
	}
	InputStream getResourceStream(String uri) throws IOException {
		String httpUri = httpize(getDavURIAsItem(uri));
		InputStream is = sardine.getInputStream(httpUri);
		return is;
	}
	
	WebserverData createWebserverContent(com.googlecode.sardine.DavResource resource) {
		WebserverData r = new WebserverData();
		r.modifiedAt = resource.getModified().getTime();
		r.createdAt = resource.getCreation().getTime();
		r.contentType = resource.getContentType();
		r.contentLength = resource.getContentLength();
		return r;
	}

	boolean existsItem(String uri) throws IOException {
		try {
			return null != getSardineResource(uri);
		} catch (SardineException ex) {
			Message.debug("URI: " + uri + ", status: " + ex.getStatusCode()
					+ "; response: " + ex.getResponsePhrase() + "; message: "
					+ ex.getMessage());
			int st = ex.getStatusCode();
			return st != 404;
		}
	}

	boolean existsPath(String uri) throws IOException {
		try {
			List<com.googlecode.sardine.DavResource> resources = getSardineDirectoryResources(uri);
			return resources != null;
		}
		catch (SardineException ex) {
			Message.debug("URI: " + uri
					+ ", status: " + ex.getStatusCode() + "; response: "
					+ ex.getResponsePhrase() + "; message: " + ex.getMessage());
			int st = ex.getStatusCode();
			return st != 404;
		}
	}
    boolean ensurePathExists(String uri) throws IOException {
		if (!existsPath(uri)) {
			Message.debug(uri+" does not exist");
			String parentURI = getParentOf(uri);
			if (parentURI == null) {
				// we are at the root level, we know the root level exists. 
				sardine.createDirectory(httpize(combine(urlRoot,uri,true)));
				return true;
			}
			else if (ensurePathExists(parentURI)) {
				sardine.createDirectory(httpize(combine(urlRoot,uri,true)));
				return true;
			}
			return false;
		}
		Message.debug(uri+" exists");
		return true;
	}
	String getParentOf(String uri) {
		int idx = uri.lastIndexOf("/");
		if (idx >= 0) {
			String container = uri.substring(0,idx);
			return container;
		}
		return null;
	}
	
	
	void putResource(String uri, InputStream is) throws IOException {
		try {
			String parentURI = getParentOf(uri);
			// create path if it doesn't exist
			if (parentURI != null && ensurePathExists(parentURI)) {
				putSardineResource(uri, is);
			}
			else {
				throw new IOException("Could not create container for "+uri);
			}
		}
		catch (SardineException ex) {
			throw new IOException("Failed to put", ex);
		}
	}
	
	private void putSardineResource(String uri, InputStream is) throws IOException {
		String httpUri = httpize(getDavURIAsItem(uri));
		try {
			sardine.put(httpUri, is);
		}
		catch (SardineException ex) {
			throw new IOException("Failed to put to "+httpUri, ex);
		}
	}
	
	void deleteFile(String uri) throws IOException {
		String processedUri = getDavURIAsItem(uri);
		deleteResource(processedUri);
	}
	void deleteDirectory(String uri) throws IOException {
		String processedUri = getDavURIAsPath(uri);
		deleteResource(processedUri);
	}
	void deleteResource(String processedUri) throws IOException {
		String httpUri = httpize(processedUri);
		try {
			sardine.delete(httpUri);
		}
		catch (SardineException ex) {
			throw new IOException("Failed to delete "+httpUri, ex);
		}
	}
}

