package info.thinkingcloud.info.tools.image.server.services;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;

import javax.annotation.PostConstruct;

import net.sf.json.JSONObject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConfigService {

	private JSONObject config;

	private static final Logger logger = LoggerFactory
			.getLogger(ConfigService.class);

	@PostConstruct
	public void init() throws IOException {
		String config = System.getProperty("config");
		if (config == null) {
			config = "/etc/image.server/config.json";
		}
		File configFile = new File(config);

		logger.debug("Reading configuration from file {}", configFile);
		if (configFile.exists()) {
			StringWriter out = new StringWriter();
			FileReader in = new FileReader(configFile);
			IOUtils.copy(in, out);
			in.close();
			out.close();
			this.config = JSONObject.fromObject(out.toString());
			logger.debug("Getting configration: {}", out.toString());
		} else {
			logger.debug("No configuration found!");
			this.config = new JSONObject();
		}
	}

	public String getCacheImageName(String uri, String source, int size)
			throws IOException {
		StringBuilder name = new StringBuilder(size);
		name.append("_").append(DigestUtils.md5Hex(uri));
		String folder = getCacheFolder(source);
		return FilenameUtils.concat(folder, name.toString());
	}

	public String getImagickBinPath() {
		if (config.has("imagick_bin_path")) {
			return config.getString("imagick_bin_path");
		}
		return "/opt/local/bin/";
	}

	private HashMap<String, String> getImageSources() {
		if (config.has("image_sources")) {
			HashMap<String, String> ret = new HashMap<String, String>();
			JSONObject obj = config.getJSONObject("image_sources");
			for (Object key : obj.keySet()) {
				ret.put(key.toString(), String.valueOf(obj.get(key)));
			}
			return ret;
		}
		return new HashMap<String, String>();
	}

	public String[] getImageSources(String source) throws IOException {
		HashMap<String, String> map = getImageSources();
		ArrayList<String> ret = new ArrayList<String>();
		if (source == null || source.equals("default")) {
			// If no source name is provided, get all the sources
			ret.addAll(map.values());
		} else {
			String path = map.get(source);

			if (path == null) // If source name is provided, but can't found,
								// fail this operation
				return new String[0];

			ret.add(map.get(source));
		}
		// Always add downloads folder first, so that, will use download file by
		// default
		ret.add(0, FilenameUtils.concat(
				FilenameUtils.concat(getCacheFolder(), "downloads"), source));
		return ret.toArray(new String[0]);
	}

	public String getCacheFolder() throws IOException {
		return getCacheFolder(null);
	}

	public String getCacheFolder(String source) throws IOException {
		String prefix = null;
		if (config.has("cache_folder"))
			prefix = config.getString("cache_folder");

		else
			prefix = "/tmp/rdisk";
		if (source != null) {
			return FilenameUtils.concat(prefix, source);
		}
		return prefix;
	}

	public JSONObject getConfig() {
		return config;
	}
}
