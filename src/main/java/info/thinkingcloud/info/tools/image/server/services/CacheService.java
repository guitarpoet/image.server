package info.thinkingcloud.info.tools.image.server.services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.annotation.PostConstruct;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CacheService {

	@Autowired
	private ConfigService config;

	private HttpClient client;

	@PostConstruct
	public void init() {
		client = HttpClientBuilder.create().build();
	}

	public String getCachedImageName(String uri, int size, String source)
			throws IOException {
		StringBuilder sb = new StringBuilder().append(size);
		sb.append("_").append(DigestUtils.md5Hex(uri));
		return FilenameUtils.concat(config.getCacheFolder(source),
				sb.toString());
	}

	public boolean shouldUpdate(String uri, String source, int size)
			throws IOException {
		String path = getCachedImageName(uri, size, source);
		File f = new File(path);
		if (f.exists()) {
			return false;
		} else
			return true;
	}

	public void serve(String uri, String source, int size, OutputStream out)
			throws IOException {
		String path = getCachedImageName(uri, size, source);
		File f = new File(path);
		IOUtils.copy(new FileInputStream(f), out);
		out.flush();
		out.close();
	}

	public void save(String uri, String source, int size, InputStream in)
			throws FileNotFoundException, IOException {
		String path = getCachedImageName(uri, size, source);
		File f = new File(path);
		if (!f.getParentFile().exists()) {
			f.getParentFile().mkdirs();
		}
		IOUtils.copy(in, new FileOutputStream(f));
	}

	/**
	 * Download the file using uri, and save the file into source using filename
	 * 
	 * @param uri
	 * @param source
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	public String download(String uri, String source, String filename)
			throws IOException {
		String folder = config.getCacheFolder(source);
		HttpGet get = new HttpGet(uri);
		HttpResponse resp = client.execute(get);
		if (resp.getStatusLine().getStatusCode() == 200) {
			File outfile = new File(FilenameUtils.concat(folder,
					FilenameUtils.getName(uri)));
			if (!outfile.getParentFile().exists()) {
				// Making the parent folders if needed
				outfile.getParentFile().mkdirs();
			}

			// Save the data into cache
			FileOutputStream out = new FileOutputStream(outfile);
			IOUtils.copy(resp.getEntity().getContent(), out);
			out.flush();
			out.close();
			return outfile.getAbsolutePath();
		}
		return null;
	}
}
