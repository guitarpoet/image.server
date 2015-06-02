package info.thinkingcloud.info.tools.image.server.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FilenameUtils;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.im4java.process.Pipe;
import org.im4java.process.ProcessStarter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ImageService {

	private static final Logger logger = LoggerFactory
			.getLogger(ImageService.class);

	@Autowired
	private ConfigService config;

	@Autowired
	private CacheService cache;

	@PostConstruct
	public void init() {
		logger.debug("Initializing im4java using imagick bin path {}",
				config.getImagickBinPath());
		ProcessStarter.setGlobalSearchPath(config.getImagickBinPath());
	}

	public boolean resize(String uri, int size, String source, OutputStream out)
			throws IOException, InterruptedException, IM4JavaException {
		String[] paths = config.getImageSources(source);

		for (String path : paths) {
			String p = null;
			if (path.startsWith("http://") || path.startsWith("https://")) {
				// This path is a url base, download it
				p = cache.download(path + "/" + uri, source, uri);
			} else {
				p = FilenameUtils.concat(path, uri);
			}

			File f = new File(p);
			logger.debug("Trying to resize file {}", f);
			if (f.exists()) {
				logger.info("Resizing file {} using size {}", f, size);
				return resize(f, size, out);
			}
		}
		return false;
	}

	public synchronized boolean resize(File f, int size, OutputStream out)
			throws IOException, InterruptedException, IM4JavaException {
		logger.debug("Resizing image {} to size {}", f, size);
		ConvertCmd convert = new ConvertCmd();
		RandomAccessFile rf = new RandomAccessFile(f, "rw");
		FileChannel channel = rf.getChannel();
		FileLock fl = channel.tryLock();
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		try {
			byte[] buffer = new byte[1024];
			while (rf.read(buffer) != -1) {
				bo.write(buffer);
			}
		} finally {
			fl.release();
		}
		rf.close();
		Pipe p = new Pipe(new ByteArrayInputStream(bo.toByteArray()), out);
		convert.setInputProvider(p);
		convert.setOutputConsumer(p);

		IMOperation oper = new IMOperation();
		oper.addImage();
		oper.resize(size, size);
		oper.addImage();
		convert.run(oper, "-", "-");
		return true;
	}

	public static void main(String[] args) throws IOException,
			InterruptedException, IM4JavaException {
		ProcessStarter.setGlobalSearchPath("/opt/local/bin");
		ImageService service = new ImageService();
		service.resize(new File("/tmp/a.jpg"), 100, new FileOutputStream(
				"/tmp/b.jpg"));
	}
}
