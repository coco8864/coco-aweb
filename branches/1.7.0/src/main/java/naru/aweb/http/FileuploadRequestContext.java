package naru.aweb.http;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.fileupload.RequestContext;

public class FileuploadRequestContext implements RequestContext {
	private String characterEncoding="utf-8";
	private int contentLength;
	private String contentType;
	private InputStream inputStream;

	public void setup(String contentType,int contentLength,InputStream inputStream,String characterEncoding){
		this.contentType=contentType;
		this.contentLength=contentLength;
		this.inputStream=inputStream;
		this.characterEncoding=characterEncoding;
	}
	
	public String getCharacterEncoding() {
		return characterEncoding;
	}

	public int getContentLength() {
		return contentLength;
	}

	public String getContentType() {
		return contentType;
	}

	public InputStream getInputStream() throws IOException {
		return inputStream;
	}
}
