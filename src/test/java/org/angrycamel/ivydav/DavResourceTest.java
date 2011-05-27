package org.angrycamel.ivydav;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;

import org.angrycamel.ivydav.DavRepository.WebserverData;
import org.apache.ivy.plugins.repository.Resource;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DavResourceTest {

	private Mockery mockery = new JUnit4Mockery() {{
		setImposteriser(ClassImposteriser.INSTANCE);
	}};
	
	private DavResource davResource = new DavResource();
	
	private DavRepository davRepo = mockery.mock(DavRepository.class);
	
	private static final int STREAMLEN = 3;
	private final InputStream stream = new ByteArrayInputStream(new byte[STREAMLEN]);
	private final WebserverData content = new WebserverData();
	{
		long lmTime = System.currentTimeMillis();
		content.contentLength = STREAMLEN;
		content.contentType = "application/octet-stream";
		content.createdAt = 0;
		content.modifiedAt = lmTime;
	}
	private final WebserverData directoryContent = new WebserverData();
	{
		long lmTime = System.currentTimeMillis();
		directoryContent.contentLength = 0;
		directoryContent.contentType = DavRepository.DIRECTORY_CONTENT_TYPE;
		directoryContent.createdAt = 0;
		directoryContent.modifiedAt = lmTime;
	}


	@Before
	public void setUp() throws Exception {
		davResource.setRepository(davRepo);
	}

	@Test
	public void testGetChildren() throws IOException {
		mockery.checking(new Expectations() {{
			allowing(davRepo).existsItem("/hello"); will(returnValue(false)); 
			allowing(davRepo).getResourceMetadata("/hello"); will(returnValue(directoryContent));
			allowing(davRepo).getDirectoryResources("/hello"); will(returnValue(Arrays.asList(new String[] { "/hello/world" }))); 
		}});
		
		davResource.setURI("/hello");
		davResource.getChildren();
	}

	@Test
	public void testClone() throws IOException {
		Resource newone = davResource.clone("/other/name");
		assertTrue("unexpected type", newone instanceof DavResource);
		DavResource dr = (DavResource)newone;
		
		assertEquals("different dav repos", dr.repository, davResource.repository);
		assertEquals("correct name", dr.uri, "/other/name");
	}
	
	@Test
	public void testOpenStream() throws IOException {
		davResource.setURI("/hello/world");
		mockery.checking(new Expectations() {{
			oneOf(davRepo).getResourceMetadata("/hello/world"); will(returnValue(content)); 
			oneOf(davRepo).getResourceStream("/hello/world"); will(returnValue(stream)); 
		}});
		InputStream is = davResource.openStream();
		assertEquals("input stream", stream, is);
	}

	@Test
	public void testPutFromStream() throws IOException {
		davResource.setURI("/hello/world");
		final InputStream tis = new ByteArrayInputStream(new byte[0]);
		mockery.checking(new Expectations() {{
			allowing(davRepo).existsItem("/hello/world"); will(returnValue(false));
			allowing(davRepo).ensurePathExists("/hello/");
			oneOf(davRepo).putResource("/hello/world",tis);
		}});
		davResource.putFromStream(tis);
	}
	
	@Test
	public void testGetContentLength() throws IOException {
		davResource.setURI("/hello/world");
		mockery.checking(new Expectations() {{
			oneOf(davRepo).getResourceMetadata("/hello/world"); will(returnValue(content)); 
		}});
		long len = davResource.getContentLength();
		assertEquals("should be 3", 3, len);
	}

	@Test
	public void testGetContentLengthForAbsentResource() throws IOException {
		davResource.setURI("/hello/world");
		mockery.checking(new Expectations() {{
			allowing(davRepo).getResourceMetadata("/hello/world"); will(returnValue(null));
		}});
		long len = davResource.getContentLength();
		assertEquals("should be -1", -1, len);
	}

	@Test
	public void testGetLastModifiedForAbsentResource() throws IOException {
		davResource.setURI("/hello/world");
		mockery.checking(new Expectations() {{
			allowing(davRepo).getResourceMetadata("/hello/world"); will(returnValue(null));
		}});
		long lm = davResource.getLastModified();
		assertEquals("should be 0", 0, lm);
	}


	@Test
	public void testGetLastModified() throws IOException {
		davResource.setURI("/hello/world");
		mockery.checking(new Expectations() {{
			oneOf(davRepo).getResourceMetadata("/hello/world"); will(returnValue(content));
		}});
		long lm = davResource.getLastModified();
		assertEquals("wrong last modified time", content.getModifiedAt(), lm);
	}


	@Test
	public void testGetName() throws IOException {
		final String setname = "/hello/world";
		davResource.setURI(setname);
		String name = davResource.getName();
		assertEquals("wrong name", setname, name);
	}
	
	@Test
	public void testExists() throws IOException {
		davResource.setURI("/hello/world");
		mockery.checking(new Expectations() {{
			oneOf(davRepo).getResourceMetadata("/hello/world"); will(returnValue(content));
		}});
		boolean exists = davResource.exists();
		assertEquals("should return true", true, exists);
	}

	@Test
	public void testIsLocal() throws IOException {
		davResource.setURI("/hello/world");
		boolean local = davResource.isLocal();
		assertEquals("should return false", false, local);
	}
	
	@Test
	public void testIsDirectoryForItem() throws IOException {
		davResource.setURI("/hello/world");
		mockery.checking(new Expectations() {{
			oneOf(davRepo).getResourceMetadata("/hello/world"); will(returnValue(content));
		}});
		boolean isdir = davResource.isDirectory();
		assertEquals("should return false", false, isdir);
	}

	@Test
	public void testIsDirectoryForDirectory() throws IOException {
		davResource.setURI("/hello/world");
		mockery.checking(new Expectations() {{
			oneOf(davRepo).getResourceMetadata("/hello/world"); will(returnValue(directoryContent));
		}});
		boolean isdir = davResource.isDirectory();
		assertEquals("should return false", true, isdir);
	}
}
