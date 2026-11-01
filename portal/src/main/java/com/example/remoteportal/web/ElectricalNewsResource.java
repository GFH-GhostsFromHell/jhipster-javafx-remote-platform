package com.example.remoteportal.web;

import com.example.remoteportal.service.ElectricalNewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/news")
public class ElectricalNewsResource {
    private final ElectricalNewsService news;

    public ElectricalNewsResource(ElectricalNewsService news) {
        this.news = news;
    }

    @GetMapping("/electrical")
    public ElectricalNewsService.Snapshot electrical() {
        return news.current();
    }
}
