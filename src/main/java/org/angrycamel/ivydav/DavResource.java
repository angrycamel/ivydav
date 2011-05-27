package org.angrycamel.ivydav;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.angrycamel.ivydav.DavRepository.WebserverData;
import org.apache.http.annotation.GuardedBy;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.util.Message;

import com.googlecode.sardine.util.SardineException;

public class DavResource implements org.apache.ivy.plugins.repository.Resource {

	org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DavResource.class);
	
	DavRepository repository = null;
    String uri = null;

//    private transient boolean init = false;
//    private transient boolean exists = false;
//    private transient boolean isDirectory = false;
    
    @GuardedBy(value="this")
    private transient WebserverData cachedContent = null;
    
	public DavResource() {
	}
	
	void setRepository(DavRepository rep) {
		this.repository = rep;
	}
	
	void setURI(String uri) {
		this.uri = uri;
	}
		
    private void init() {
    }
    /*
    private boolean isContainerURI() {
    	return uri.endsWith("/");
    }
    
    private boolean initContainer() {
    	try {
    		List<String> childResources = repository.getDirectoryResources(uri);
    		exists = childResources != null && !childResources.isEmpty();
    		isDirectory = childResources.size() > 1;
        	return true;
    	} 
    	catch (IOException ex) {
    		Message.verbose(ex.getLocalizedMessage());
           	exists = false;
           	return false;
    	}
    }
    private boolean initItem() {
		try {
			exists = repository.existsItem(uri);
			isDirectory = false;
			return true;
        } 
		catch (Exception e2) {
        	Message.verbose(e2.getLocalizedMessage());
        	exists = false;
        	return false;
        }
    }
    */
    
    /**
     * Get a list of direct descendents of the given resource. Note that attempts to get a list of
     * children does <emphasize>not</emphasize> result in an error. Instead an error message is
     * logged and an empty ArrayList returned.
     */
    public List<String> getChildren() {
        init();
        Existence type = existence();
        if (Existence.ABSENT.equals(type)) {
        	Message.error(uri+" does not exist; cannot enumerate children!");
        	return Collections.emptyList();
        }
        else if (Existence.FILE.equals(type)) {
           	Message.error(uri+" exists but is not a directory; cannot enumerate children");
           	return Collections.emptyList();
        }
        List<String> list = null;
        try {
        	list = repository.getDirectoryResources(uri);
        } catch (IOException e) {
            Message.verbose(e.getLocalizedMessage());
        }
        return list;
    }
    
    boolean isDirectory() {
    	init();
        try {
	        WebserverData c = fetchWebserverContent();
	        if (c == null) return false;
	        String type = c.getContentType();
	        if (type == null) return false;
	        return type.equals(DavRepository.DIRECTORY_CONTENT_TYPE);
        }
        catch (IOException ex) {
        	LOG.warn("Error fetching from server", ex);
        }
        return false;
    }
    
    public String getName() {
        return normalize(uri);
    }

    public Resource clone(String cloneName) {
    	DavResource r = new DavResource();
    	r.setRepository(this.repository);
    	r.setURI(cloneName);
    	return r;
    }

    public static String normalize(String davURI) {
        if (davURI == null) {
            return "";
        }

        return davURI;
    }

    public long getLastModified() {
        init();
        try {
	        WebserverData c = fetchWebserverContent();
	        return c == null ? 0 : c.getModifiedAt();
        }
        catch (IOException ex) {
        	LOG.warn("Error fetching from server", ex);
        }
        return 0;
    }

    public boolean exists() {
        init();
        try {
	        WebserverData metadata = fetchWebserverContent();
	        return metadata == null ? false : metadata.contentType != null;
        }
        catch (IOException ex) {
        	LOG.warn("Error checking existence of "+uri, ex);
        }
        return false;
    }
    static enum Existence {
    	DIRECTORY,
    	FILE,
    	ABSENT;
    }
    
    private Existence existence() {
        init();
        boolean dirish = uri.endsWith("/");
        try {
	        WebserverData metadata = fetchWebserverContent();
	        return metadata == null ? Existence.ABSENT : (DavRepository.DIRECTORY_CONTENT_TYPE.equals(metadata.getContentType()) ? Existence.DIRECTORY : Existence.FILE);
        }
        catch (SardineException ex) {
        	if (ex.getStatusCode() == 301) {
        		return dirish ? Existence.FILE : Existence.DIRECTORY;
        	}
        }
        catch (IOException ex) {
        	LOG.error("Error checking existence of "+uri, ex);
        }
        return Existence.ABSENT;
    }

    public String toString() {
    	return getName();
    }

    public boolean isLocal() {
    	init();
    	return false;
    }

    public InputStream openStream() throws IOException {
    	init();
    	WebserverData content = fetchWebserverContent();
    	return content == null ? null : repository.getResourceStream(uri);
    }
    
    private synchronized WebserverData fetchWebserverContent() throws IOException {
    	if (cachedContent == null) {
    		cachedContent = repository.getResourceMetadata(uri);
    	}
    	return cachedContent;
    }

	@Override
	public long getContentLength() {
    	init();
    	try {
	    	WebserverData content = fetchWebserverContent();
			return content == null ? -1 : content.getContentLength();
    	}
    	catch (IOException ex) {
    		LOG.warn("error fetching content length", ex);
    	}
		return -1;
	}	
	
	public void putFromStream(InputStream is) throws IOException {
    	init();
		repository.putResource(uri, is);
	}
	
	void delete() throws IOException {
		init();
        Existence type = existence();
        if (Existence.ABSENT.equals(type)) {
        	Message.error(uri+" does not exist; cannot delele!");
        	return;
        }
        else if (Existence.FILE.equals(type)) {
    		repository.deleteFile(uri);
        }
        else {
    		repository.deleteDirectory(uri);
        }
	}
}