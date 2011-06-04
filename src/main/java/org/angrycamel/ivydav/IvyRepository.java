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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.plugins.repository.AbstractRepository;
import org.apache.ivy.plugins.repository.RepositoryCopyProgressListener;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.TransferEvent;
import org.apache.ivy.util.CopyProgressListener;
import org.apache.ivy.util.FileUtil;
import org.apache.ivy.util.Message;

/**
 * Represents a remote webdav share at a particular HTTP URL requiring specific
 * credentials
 * 
 * NB HTTPS is not supported (yet)
 */
public class IvyRepository extends AbstractRepository {

	static final String PROTOCOL = "webdav";	
	
	DavRepository davRepo;

	private final CopyProgressListener progress = new RepositoryCopyProgressListener(
			this);

	public IvyRepository(String root, String user, String pass) {
		davRepo = new DavRepository(root, user, pass);
	}

	@Override
	public Resource getResource(String davURI) throws IOException {
		DavResource dr = new DavResource();
		dr.setRepository(davRepo);
		dr.setURI(davURI);
		return dr;
	}

	/**
	 * Transfer a Resource from the repository via webdav to the local file
	 * system.
	 * 
	 * @param srcURI
	 *            a <code>String</code> identifying the DAV resource to be
	 *            fetched
	 * @param destination
	 *            a <code>File</code> identifying the destination file
	 * @throws <code>IOException</code> on failure
	 */
	@Override
	public void get(String srcDavURI, File destination) throws IOException {
		DavResource src = new DavResource();
		src.setRepository(davRepo);
		src.setURI(srcDavURI);
		fireTransferInitiated(src, TransferEvent.REQUEST_GET);
		InputStream is = null;
		try {
			is = src.openStream();
			FileUtil.copy(is, destination, progress);
		} catch (IOException ex) {
			fireTransferError(ex);
			throw ex;
		} catch (RuntimeException ex) {
			fireTransferError(ex);
			throw ex;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (Exception e) {
				}
			}
		}
	}

	/**
	 * Return a listing of the contents of a parent directory. Listing is a set
	 * of strings representing webdav:// URIs.
	 * 
	 * @param davURI
	 *            providing identifying a webdav provided resource
	 * @throws IOException
	 *             on failure.
	 */
	@Override
	public List<String> list(String davURI) throws IOException {
		ArrayList<String> list = new ArrayList<String>();
		Message.debug("list called for URI" + davURI);
		DavResource dr = new DavResource();
		dr.setRepository(davRepo);
		dr.setURI(davURI);
		List<String> children = dr.getChildren();
		for (String childURI : children) {
			Message.debug("child " + childURI);
			if (!finalPartStartsWith(childURI, ".")) {
				list.add(childURI);
			}
		}
		return list;
	}

	private boolean finalPartStartsWith(String uri, String prefix) {
		String checkThis = uri.endsWith(getFileSeparator()) ? uri.substring(0,
				uri.length() - getFileSeparator().length()) : uri;
		int idx = checkThis.lastIndexOf(getFileSeparator());
		if (idx < 0) {
			return checkThis.startsWith(prefix);
		} else {
			return checkThis.substring(idx + 1).startsWith(prefix);
		}
	}

	/**
	 * Transfer an Ivy resource to a webdav repository
	 * 
	 * @param source
	 *            a <code>File</code> indentifying the local file to transfer to
	 *            the repository
	 * @param davURI
	 *            a <code>String</code> URI identifying the destination webdav
	 *            Resource.
	 * @param overwrite
	 *            whether to overwrite an existing resource.
	 */
	@Override
	public void put(File source, String davURI, boolean overwrite)
			throws IOException {
		DavResource dest = new DavResource();
		dest.setRepository(davRepo);
		dest.setURI(davURI);
		fireTransferInitiated(dest, TransferEvent.REQUEST_PUT);
		if (dest.exists() && !overwrite) {
			throw new IOException("Cannot copy. Destination file: "
					+ dest.getName() + " exists and overwrite not set.");
		}
		FileInputStream fis = new FileInputStream(source);
		try {
			dest.putFromStream(fis);
			fireTransferCompleted();
		} catch (Exception ex) {
			fireTransferError(ex);
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (Exception ex) {
				}
			}
		}
	}

	
	// experimental
	
	public void delete(Artifact artifact, String dest) throws IOException {
		DavResource resource = new DavResource();
		resource.setRepository(davRepo);
		resource.setURI(dest);
		if (!resource.exists()) {
			throw new IOException("Cannot delete file: "
					+ resource.getName() + " - does not exist");
		}
		try {
			resource.delete();
		} catch (Exception ex) {
			fireTransferError(ex);
		}
	}
	public void deleteAll(String dest) throws IOException {
		File f = new File(dest);
		String pathOnly = f.getParent();
		DavResource resource = new DavResource();
		resource.setRepository(davRepo);
		resource.setURI(pathOnly);
		try {
			resource.delete();
		} catch (Exception ex) {
			fireTransferError(ex);
		}
	}

}