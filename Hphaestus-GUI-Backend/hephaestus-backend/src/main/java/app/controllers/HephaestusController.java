package app.controllers;

import app.model.SelectedQuery;
import app.services.HephaestusService;
import conf.Configuration;
import io.github.hephaestusmetrics.model.queryresults.RawQueryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("hephaestus")
@CrossOrigin(origins = Configuration.GUI_ORIGINS)
@Log4j2
@RequiredArgsConstructor
public class HephaestusController {

    private final HephaestusService hephaestusService;

    @RequestMapping(value = "/queries/simple", method = RequestMethod.GET, produces = "application/json")
    public List<SelectedQuery> getSavedSimple() {
        return hephaestusService.getSaved(false);
    }

    @RequestMapping(value = "/queries/custom", method = RequestMethod.GET, produces = "application/json")
    public List<SelectedQuery> getSavedCustom() {
        return hephaestusService.getSaved(true);
    }

    @RequestMapping(value = "/queries/simple", method = RequestMethod.PUT)
    public ResponseEntity<Void> saveSimple(@RequestBody String body) {
        return hephaestusService.saveSimpleQueries(body);
    }

    @RequestMapping(value = "/queries/custom", method = RequestMethod.PUT)
    public ResponseEntity<Void> saveCustom(@RequestBody String body) {
        return hephaestusService.saveCustomQueries(body);
    }

    @RequestMapping(value = "/metrics/selected", method = RequestMethod.GET)
    public List<RawQueryResult> getSelected() {
        return hephaestusService.getSelected();
    }

}


