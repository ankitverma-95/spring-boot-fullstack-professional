package com.example.demo.service;

import com.example.demo.entity.Site;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteService {

    private final SiteRepository siteRepository;

    @Transactional
    public Site createSite(Site site) {
        log.info("Creating new site: {}", site.getSiteName());
        return siteRepository.save(site);
    }

    public Site getSiteById(Long id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SITE_NOT_FOUND", "Site not found with ID: " + id));
    }

    public List<Site> getAllSites() {
        return siteRepository.findAll();
    }
}
