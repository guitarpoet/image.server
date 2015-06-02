package info.thinkingcloud.info.tools.image.server;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileCacheImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONObject;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.imgscalr.Scalr;
import org.springframework.util.DigestUtils;

public class ResizeServlet extends HttpServlet {
	private static final long serialVersionUID = 1487521497197747927L;
	private static final int DEFAULT_BUFFER_SIZE = 10240; // ..bytes = 10KB.

	private JSONObject config;

	private JSONObject getConfig() throws IOException {
		if (this.config == null) {
			String config = System.getProperty("config");
			if (config == null) {
				config = "/etc/image.server/config.json";
			}
			File configFile = new File(config);
			if (configFile.exists()) {
				StringWriter out = new StringWriter();
				FileReader in = new FileReader(configFile);
				IOUtils.copy(in, out);
				in.close();
				out.close();
				this.config = JSONObject.fromObject(out.toString());
			} else
				this.config = new JSONObject();
		}
		return this.config;
	}

	private String[] getImgFolders() throws IOException {
		JSONObject config = getConfig();
		if (config.has("image_folder")) {
			ArrayList<String> ret = new ArrayList<String>();
			for (Object o : config.getJSONArray("image_folder").toArray()) {
				ret.add(String.valueOf(o));
			}
			return ret.toArray(new String[0]);
		}
		return new String[] { "/tmp/rdisk" };
	}

	private String getCacheFolder() throws IOException {
		JSONObject config = getConfig();
		if (config.has("cache_folder"))
			return config.getString("cache_folder");

		return "/tmp/rdisk";
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		String[] imgfolders = getImgFolders();
		String cachefolder = getCacheFolder();

		String sizep = req.getParameter("size").replace("px", "");
		if (sizep == null) {
			throw new IllegalArgumentException("No size");
		}

		String filename = req.getRequestURI().replace("/image.server", "")
				.replace("/resize", "");

		String type = FilenameUtils.getExtension(filename);

		resp.setContentType(getServletContext().getMimeType(filename));

		// Find the image
		String file = null;
		File f = null;
		for (String imgfolder : imgfolders) {
			file = imgfolder + filename;
			f = new File(file);
			if (f.exists())
				break;
			else
				f = null;
		}

		if (f == null) {
			throw new IllegalArgumentException("File " + file + " not exists!");
		}

		// Find the cache file
		StringBuilder sb = new StringBuilder(cachefolder).append("/")
				.append(sizep).append("_")
				.append(DigestUtils.md5DigestAsHex(filename.getBytes()))
				.append(".").append(type);
		File cachedFile = new File(sb.toString());
		OutputStream out = resp.getOutputStream();

		long length = file.length();
		long lastModified = f.lastModified();
		String eTag = file + "_" + length + "_" + lastModified;
		long expires = System.currentTimeMillis() + 2592000;

		// Validate request headers for caching
		// ---------------------------------------------------

		// If-None-Match header should contain "*" or ETag. If so, then return
		// 304.
		String ifNoneMatch = req.getHeader("If-None-Match");
		if (ifNoneMatch != null && matches(ifNoneMatch, eTag)) {
			resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			resp.setHeader("ETag", eTag); // Required in 304.
			resp.setDateHeader("Expires", expires); // Postpone cache
			return;
		}

		// If-Modified-Since header should be greater than LastModified. If so,
		// then return 304.
		// This header is ignored if any If-None-Match header is specified.
		long ifModifiedSince = req.getDateHeader("If-Modified-Since");
		if (ifNoneMatch == null && ifModifiedSince != -1
				&& ifModifiedSince + 1000 > lastModified) {
			resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			resp.setHeader("ETag", eTag); // Required in 304.
			resp.setDateHeader("Expires", expires); // Postpone cache
			return;
		}

		// Validate request headers for resume
		// ----------------------------------------------------

		// If-Match header should contain "*" or ETag. If not, then return 412.
		String ifMatch = req.getHeader("If-Match");
		if (ifMatch != null && !matches(ifMatch, eTag)) {
			resp.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
			return;
		}

		// If-Unmodified-Since header should be greater than LastModified. If
		// not, then return 412.
		long ifUnmodifiedSince = req.getDateHeader("If-Unmodified-Since");
		if (ifUnmodifiedSince != -1 && ifUnmodifiedSince + 1000 <= lastModified) {
			resp.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
			return;
		}

		// Initialize response.
		resp.reset();
		resp.setBufferSize(DEFAULT_BUFFER_SIZE);
		resp.setHeader("ETag", eTag);
		resp.setDateHeader("Last-Modified", lastModified);
		resp.setDateHeader("Expires", expires);

		if (cachedFile.exists()) {
			FileInputStream cacheFileInput = new FileInputStream(cachedFile);
			IOUtils.copy(cacheFileInput, out);
			cacheFileInput.close();
		} else {
			// Read the image

			BufferedImage img = ImageIO.read(f);

			int width = img.getWidth();
			int height = img.getHeight();

			int size = 0;

			if (StringUtils.isNumeric(sizep)) {
				if (width > height) {
					size = (int) Math.ceil(Double.parseDouble(sizep));
				} else {
					size = (int) Math.ceil(Double.parseDouble(sizep) * height
							/ width);
				}

				// Scale it
				img = Scalr.resize(img, size);

			}

			// Write to output
			MemoryCacheImageOutputStream memory = new MemoryCacheImageOutputStream(
					out);
			ImageIO.write(img, type, memory);
			memory.close();

			FileCacheImageOutputStream fileout = new FileCacheImageOutputStream(
					new FileOutputStream(cachedFile), new File(cachefolder));
			ImageIO.write(img, type, fileout);
			fileout.close();
		}
		out.close();
	}

	/**
	 * Returns true if the given match header matches the given value.
	 * 
	 * @param matchHeader
	 *            The match header.
	 * @param toMatch
	 *            The value to be matched.
	 * @return True if the given match header matches the given value.
	 */
	private static boolean matches(String matchHeader, String toMatch) {
		String[] matchValues = matchHeader.split("\\s*,\\s*");
		Arrays.sort(matchValues);
		return Arrays.binarySearch(matchValues, toMatch) > -1
				|| Arrays.binarySearch(matchValues, "*") > -1;
	}
}
