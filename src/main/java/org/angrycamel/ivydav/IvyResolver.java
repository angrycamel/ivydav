/*
   Copyright 2011 Ed Burcher

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package org.angrycamel.ivydav;

import org.apache.ivy.plugins.repository.Repository;
import org.apache.ivy.plugins.resolver.RepositoryResolver;

/**
 * An IVY resolver for use with Sardine to access and publish to a DAV share
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

