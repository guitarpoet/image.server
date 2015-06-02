package info.thinkingcloud.info.tools.image.server.actions;

import info.thinkingcloud.info.tools.image.server.services.CacheService;
import info.thinkingcloud.info.tools.image.server.services.ImageService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.im4java.core.IM4JavaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ResizeAction {

	@Autowired
	private ImageService image;

	@Autowired
	private CacheService cache;

	@RequestMapping("/new/resize")
	public void resize(@RequestParam String size, @RequestParam String uri,
			@RequestParam(defaultValue = "default") String source,
			HttpServletRequest req, HttpServletResponse resp)
			throws IOException, InterruptedException, IM4JavaException {
		resp.setContentType(req.getSession().getServletContext()
				.getMimeType(uri));
		OutputStream out = resp.getOutputStream();
		int s = Integer.parseInt(size.replace("px", "").replace("pt", ""));

		if (cache.shouldUpdate(uri, source, s)) {
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			if (!image.resize(uri, s, source, bout)) {
				resp.setStatus(404);
				resp.setContentType("text/html");
				PrintWriter p = new PrintWriter(out, true);
				p.println("Not Found!");
			} else {
				// Let's output it to cache first
				cache.save(uri, source, s,
						new ByteArrayInputStream(bout.toByteArray()));
				// Then serve it
				cache.serve(uri, source, s, out);
			}
		} else {
			cache.serve(uri, source, s, out);
		}

		out.flush();
		out.close();
	}
}