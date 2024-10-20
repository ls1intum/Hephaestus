package de.tum.in.www1.hephaestus.meta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.admin.AdminService;

@Service
public class MetaService {
    private static final Logger logger = LoggerFactory.getLogger(MetaService.class);

    @Autowired
    private AdminService adminService;

    public MetaDataDTO getMetaData() {
        logger.info("Getting meta data...");
        return new MetaDataDTO(
                adminService.getAdminConfig().getRepositoriesToMonitor().stream().sorted().toArray(String[]::new));
    }
}
