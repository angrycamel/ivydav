package org.angrycamel.ivydav;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.angrycamel.ivydav.DavRepository.WebserverData;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.googlecode.sardine.Sardine;
import com.googlecode.sardine.util.SardineException;

@RunWith(JMock.class)
public class DavRepositoryTest {

	private Mockery mockery = new JUnit4Mockery() {{
		setImposteriser(ClassImposteriser.INSTANCE);
	}};
	
	private final Sardine sardine = mockery.mock(Sardine.class);
	private static final String ROOT = "webdav://my.server";

	private DavRepository davRepository = new DavRepository(ROOT, null, null);
	{
		davRepository.sardine = sardine;
	}
	
	private final InputStream contentStream = new ByteArrayInputStream(new byte[3]);
	
	private List<com.googlecode.sardine.DavResource> emptyDirectory(String uri) {
		return Arrays.asList(new com.googlecode.sardine.DavResource[] {
			new com.googlecode.sardine.DavResource(
					DavRepository.httpize(DavRepository.combine("webdav://my.server",uri,true)), 
					uri, 
					new Date(0), new Date(0), DavRepository.DIRECTORY_CONTENT_TYPE, 0L, true, null)
		});
	}
	private SardineException notFoundException = new SardineException("", 404, "");
	
	@Test
	public void testPutResource() throws IOException {
		final String uri = "/hello/world";
		mockery.checking(new Expectations() {{
			oneOf(sardine).put("http://my.server/hello/world", contentStream);
			// existence of parent dir:
			oneOf(sardine).getResources("http://my.server/hello/"); will(returnValue(emptyDirectory("hello/")));
		}});
		davRepository.putResource(uri, contentStream);
	}

	@Test
	public void testEnsurePathExists() throws IOException {
		final String uri = "/hello/there/mother";
		mockery.checking(new Expectations() {{
			oneOf(sardine).getResources("http://my.server/hello/there/mother/"); will(throwException(notFoundException));
			oneOf(sardine).getResources("http://my.server/hello/there/"); will(throwException(notFoundException));
			oneOf(sardine).getResources("http://my.server/hello/"); will(returnValue(emptyDirectory("hello/")));
			oneOf(sardine).createDirectory("http://my.server/hello/there/"); 
			oneOf(sardine).createDirectory("http://my.server/hello/there/mother/");
		}});
		davRepository.ensurePathExists(uri);
	}
	
	@Test
	public void testGetDirectoryResources() throws IOException {
		String uri = "/hello/";
		final List<com.googlecode.sardine.DavResource> resources = Arrays.asList(new com.googlecode.sardine.DavResource[] {
			new com.googlecode.sardine.DavResource(
					"http://my.server/hello/", 
					"hello", 
					new Date(0), new Date(0), DavRepository.DIRECTORY_CONTENT_TYPE, 0L, true, null),
			new com.googlecode.sardine.DavResource(
					"http://my.server/hello/", 
					"mother", 
					new Date(0), new Date(0), DavRepository.DIRECTORY_CONTENT_TYPE, 0L, false, null),
			new com.googlecode.sardine.DavResource(
					"http://my.server/hello/", 
					"file", 
					new Date(0), new Date(0), "test/xml", 1024L, false, null),
		});
		
		mockery.checking(new Expectations() {{
			oneOf(sardine).getResources("http://my.server/hello/"); will(returnValue(resources));
		}});
		List<String> childUris = davRepository.getDirectoryResources(uri);
		List<String> expectedChildUris = Arrays.asList(new String[] {
				"hello/mother/", "hello/file"
		});
		assertEquals("", expectedChildUris, childUris);
	}
	@Test
	public void testGetParentOf() {
		final String uri = "/hello/world";
		String parentUri = davRepository.getParentOf(uri);
		assertEquals("", "/hello", parentUri);
	}
	
	@Test
	public void testGetResourceMetadata() throws IOException {
		String uri = "/hello/world";
		final com.googlecode.sardine.DavResource resource = new com.googlecode.sardine.DavResource(
				"http://my.server/hello/",
				"world",
				new Date(0), new Date(0),
				"text/xml", 1024L, false, null
		);
		mockery.checking(new Expectations() {{
			oneOf(sardine).getResources("http://my.server/hello/world"); will(returnValue(Collections.singletonList(resource)));
		}});
		WebserverData expected = new WebserverData();
		expected.contentLength = 1024L;
		expected.contentType = "text/xml";
		expected.createdAt = 0L;
		expected.modifiedAt = 0L;
		WebserverData data = davRepository.getResourceMetadata(uri);
		assertEquals("", expected, data);
	}
	
	@Test
	public void testGetResourceStream() throws IOException {
		String uri = "/hello/world";
		mockery.checking(new Expectations() {{
			oneOf(sardine).getInputStream("http://my.server/hello/world"); will(returnValue(contentStream));
		}});
		InputStream is = davRepository.getResourceStream(uri);
		assertEquals("", contentStream, is);
	}
}

