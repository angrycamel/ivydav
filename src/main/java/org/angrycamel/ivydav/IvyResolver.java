package org.angrycamel.ivydav;

import org.apache.ivy.plugins.repository.Repository;
import org.apache.ivy.plugins.resolver.RepositoryResolver;

/**
 * An IVY resolver for use with Sardine to access and publish to a DAV share
 * @author ed
 */
public class IvyResolver extends RepositoryResolver {

	private boolean inited = false;
	private String davUser;
	private String davPassword;
    private String davRoot;
    
    public void setroot(String root) {
    	this.davRoot = root;
    }
    
    public void setdavUser(String user) {
    	this.davUser = user;
    }
    
    public void setdavPassword(String password) {
    	this.davPassword = password;
    }
    
    private synchronized void init() {
    	if (!inited) {
            inited=true;
            IvyRepository rep = new IvyRepository(davRoot, davUser, davPassword);
            setRepository(rep);
    	}
    }
    
    public IvyResolver() {
    }
    
    @Override
    public Repository getRepository() {
    	init();
    	return super.getRepository();
    }
    
    public String getTypeName() {
        return IvyRepository.PROTOCOL;
    }

    public String hidePassword(String name) {
    	if (davUser == null) {
            return "["+davRoot+"] /"+name;
    	}
    	else {
            return "["+davUser+"/******* @ "+davRoot+"] /"+name;
    	}
    }

}

