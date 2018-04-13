package cern.ais.gridwars.web.controller;

import cern.ais.gridwars.runtime.MatchRuntimeConstants;
import cern.ais.gridwars.runtime.MatchTurnStateSerializer;
import cern.ais.gridwars.web.config.GridWarsProperties;
import cern.ais.gridwars.web.controller.error.NotFoundException;
import cern.ais.gridwars.web.service.MatchService;
import cern.ais.gridwars.web.util.FileUtils;
import cern.ais.gridwars.web.util.ModelAndViewBuilder;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;


@Controller
@RequestMapping("/match")
public class MatchController {

    private static final String GZIP = "gzip";

    private final MatchTurnStateSerializer serializer = new MatchTurnStateSerializer();
    private final MatchService matchService;
    private final GridWarsProperties gridWarsProperties;

    public MatchController(MatchService matchService, GridWarsProperties gridWarsProperties) {
        this.matchService = Objects.requireNonNull(matchService);
        this.gridWarsProperties = Objects.requireNonNull(gridWarsProperties);
    }

    @GetMapping("/list")
    public ModelAndView list() {
        return ModelAndViewBuilder.forPage("match/list")
            .addAttribute("matches", matchService.findAllPlayedMatchesByActiveBots())
            .toModelAndView();
    }

    @GetMapping("/{matchId}")
    public ModelAndView show(@PathVariable String matchId) {
        return matchService.loadMatch(matchId)
            .map(match ->
                ModelAndViewBuilder.forPage("match/show")
                    .addAttribute("matchDataUrl", "/match/data/" + matchId)
                    .addAttribute("turnCount", match.getTurnCount())
                    .toModelAndView()
            )
            .orElseThrow(NotFoundException::new);
    }

    // IMPORTANT: This controller mapping is usually excluded from the Spring security
    // filter chain because we need to set our custom response headers (e.g. compression).
    // As a result, there won't be any user security context available in this method!
    // Normally, we don't need any security context in this method, as we only send the
    // turn state bytes, which do not contain any sensitive information whatsoever.
    @GetMapping("/data/{matchId}")
    public void matchData(@PathVariable String matchId,
                          @RequestHeader(name = HttpHeaders.ACCEPT_ENCODING, required = false) String acceptEncoding,
                          HttpServletResponse response) throws IOException {
        boolean acceptsGzipEncoding = acceptsGzipEncoding(acceptEncoding);
        String matchTurnFilePath = createMatchTurnStatesFilePath(matchId);

        Optional<InputStream> data = acceptsGzipEncoding
            ? serializer.deserializeCompressedFromFile(matchTurnFilePath)
            : serializer.deserializeUncompressedFromFile(matchTurnFilePath);

        // TODO check if it helps to check the file size and set the "Content-Length" header

        if (data.isPresent()) {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);

            if (acceptsGzipEncoding) {
                response.setHeader(HttpHeaders.CONTENT_ENCODING, GZIP);
            }

            // The turn data of a match will never change, so it can be cached "forever" by the browser
            addCacheForeverHeader(response);

            IOUtils.copy(data.get(), response.getOutputStream());
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private boolean acceptsGzipEncoding(String acceptEncoding) {
        return StringUtils.hasLength(acceptEncoding) && acceptEncoding.toLowerCase().contains(GZIP);
    }

    // TODO the method below rather belongs to a service or utility class, the controller shouldn't know the details about the file structure
    private String createMatchTurnStatesFilePath(String matchId) {
        return FileUtils.joinFilePathsToSinglePath(
            gridWarsProperties.getDirectories().getMatchesDir(),
            matchId,
            MatchRuntimeConstants.MATCH_TURNS_PAYLOAD_FILE_NAME
        );
    }

    private void addCacheForeverHeader(HttpServletResponse response) {
        response.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=31556926");
    }
}
