package com.openinfra.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openinfra.backend.exception.GitHubApiException;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class GitHubService {

    private final RestTemplate restTemplate;

    @Value("${GITHUB_TOKEN:#{environment.GITHUB_TOKEN}}")
    private String envGithubToken;

    @Value("${github.token:}")
    private String propGithubToken;

    public GitHubService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
    }

    private String getResolvedToken() {
        if (envGithubToken != null && !envGithubToken.isBlank()) {
            return envGithubToken.trim();
        }
        if (propGithubToken != null && !propGithubToken.isBlank()) {
            return propGithubToken.trim();
        }
        return null;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("User-Agent", "OpenInfraIQ-Backend/1.0");

        String token = getResolvedToken();
        if (token != null && !token.isBlank()) {
            if (token.startsWith("github_pat_")) {
                headers.set("Authorization", "Bearer " + token);
            } else {
                headers.set("Authorization", "token " + token);
            }
        }
        return headers;
    }

    private <T> T executeGet(String endpoint, Class<T> responseType) {
        String url = "https://api.github.com" + endpoint;
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
        try {
            ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            handleClientException(ex, endpoint);
            throw new GitHubApiException("GitHub request failed for " + endpoint + ": " + ex.getMessage(), ex.getStatusCode().value());
        } catch (HttpServerErrorException ex) {
            log.error("GitHub API server error on {}: {}", endpoint, ex.getMessage());
            throw new GitHubApiException("GitHub API is temporarily unavailable (HTTP " + ex.getStatusCode().value() + ")", ex.getStatusCode().value());
        } catch (Exception ex) {
            log.error("Unexpected error calling GitHub on {}: {}", endpoint, ex.getMessage());
            throw new GitHubApiException("Failed to communicate with GitHub API: " + ex.getMessage(), 500);
        }
    }

    private <T> List<T> executeGetList(String endpoint, ParameterizedTypeReference<List<T>> typeReference) {
        String url = "https://api.github.com" + endpoint;
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
        try {
            ResponseEntity<List<T>> response = restTemplate.exchange(url, HttpMethod.GET, entity, typeReference);
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND || ex.getStatusCode() == HttpStatus.NO_CONTENT) {
                return Collections.emptyList();
            }
            handleClientException(ex, endpoint);
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("Non-critical GitHub sub-query failed on {}: {}", endpoint, ex.getMessage());
            return Collections.emptyList();
        }
    }

    private void handleClientException(HttpClientErrorException ex, String endpoint) {
        if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw new GitHubApiException("Repository was not found on GitHub. Verify the owner and repository name.", 404);
        }
        if (ex.getStatusCode() == HttpStatus.FORBIDDEN || ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            String rateRemaining = ex.getResponseHeaders() != null ? ex.getResponseHeaders().getFirst("x-ratelimit-remaining") : null;
            if ("0".equals(rateRemaining)) {
                throw new GitHubApiException(
                        "GitHub API rate limit reached. Please configure a valid GITHUB_TOKEN on the backend server.",
                        429
                );
            }
            throw new GitHubApiException("GitHub API access was forbidden (HTTP 403).", 403);
        }
        if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            throw new GitHubApiException("GitHub API token configured on backend is invalid or expired.", 401);
        }
    }

    public RawGitHubRepo fetchRepositoryMetadata(String owner, String repo) {
        return executeGet("/repos/" + owner + "/" + repo, RawGitHubRepo.class);
    }

    public List<RawGitHubContributor> fetchContributors(String owner, String repo) {
        return executeGetList("/repos/" + owner + "/" + repo + "/contributors?per_page=30",
                new ParameterizedTypeReference<List<RawGitHubContributor>>() {});
    }

    public List<RawGitHubRelease> fetchReleases(String owner, String repo) {
        return executeGetList("/repos/" + owner + "/" + repo + "/releases?per_page=15",
                new ParameterizedTypeReference<List<RawGitHubRelease>>() {});
    }

    public List<RawGitHubCommit> fetchRecentCommits(String owner, String repo) {
        return executeGetList("/repos/" + owner + "/" + repo + "/commits?per_page=30",
                new ParameterizedTypeReference<List<RawGitHubCommit>>() {});
    }

    public List<RawGitHubPull> fetchRecentPulls(String owner, String repo) {
        return executeGetList("/repos/" + owner + "/" + repo + "/pulls?state=closed&sort=updated&direction=desc&per_page=15",
                new ParameterizedTypeReference<List<RawGitHubPull>>() {});
    }

    public List<RawGitHubIssue> fetchRecentIssues(String owner, String repo) {
        return executeGetList("/repos/" + owner + "/" + repo + "/issues?state=closed&sort=updated&direction=desc&per_page=15",
                new ParameterizedTypeReference<List<RawGitHubIssue>>() {});
    }

    public List<RawGitHubRepo> searchRepositories(String query, int limit) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        int count = Math.max(1, Math.min(limit, 30));
        try {
            String encodedQuery = java.net.URLEncoder.encode(query.trim(), java.nio.charset.StandardCharsets.UTF_8);
            RawGitHubSearchResult result = executeGet(
                    "/search/repositories?q=" + encodedQuery + "&sort=stars&order=desc&per_page=" + count,
                    RawGitHubSearchResult.class
            );
            return result != null && result.getItems() != null ? result.getItems() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("GitHub repository search query '{}' failed: {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    public String fetchReadme(String owner, String repo) {
        try {
            HttpHeaders headers = createHeaders();
            headers.set("Accept", "application/vnd.github.v3.raw");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = "https://api.github.com/repos/" + owner + "/" + repo + "/readme";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String body = response.getBody();
                return body.length() > 8192 ? body.substring(0, 8192) + "... [truncated]" : body;
            }
        } catch (Exception e) {
            log.debug("Readme fetch skipped or not found for {}/{}: {}", owner, repo, e.getMessage());
        }
        return "";
    }

    // -------------------------------------------------------------------------
    // Internal DTOs representing GitHub REST API v3 Responses
    // -------------------------------------------------------------------------

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawGitHubRepo {
        private Long id;
        private String name;
        @JsonProperty("full_name")
        private String fullName;
        private Owner owner;
        private String description;
        @JsonProperty("html_url")
        private String htmlUrl;
        @JsonProperty("clone_url")
        private String cloneUrl;
        @JsonProperty("default_branch")
        private String defaultBranch;
        private String language;
        @JsonProperty("stargazers_count")
        private Integer stargazersCount;
        @JsonProperty("forks_count")
        private Integer forksCount;
        @JsonProperty("open_issues_count")
        private Integer openIssuesCount;
        @JsonProperty("subscribers_count")
        private Integer subscribersCount;
        @JsonProperty("watchers_count")
        private Integer watchersCount;
        @JsonProperty("created_at")
        private String createdAt;
        @JsonProperty("updated_at")
        private String updatedAt;
        @JsonProperty("pushed_at")
        private String pushedAt;
        @JsonProperty("private")
        private Boolean isPrivate;
        private Boolean archived;
        private License license;

        @Getter @Setter @NoArgsConstructor @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Owner {
            private String login;
            @JsonProperty("avatar_url")
            private String avatarUrl;
            private String type; // "Organization" | "User"
        }

        @Getter @Setter @NoArgsConstructor @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class License {
            private String key;
            private String name;
            @JsonProperty("spdx_id")
            private String spdxId;
        }
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawGitHubContributor {
        private String login;
        private Integer contributions;
        @JsonProperty("avatar_url")
        private String avatarUrl;
        @JsonProperty("html_url")
        private String htmlUrl;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawGitHubRelease {
        private Long id;
        @JsonProperty("tag_name")
        private String tagName;
        private String name;
        @JsonProperty("published_at")
        private String publishedAt;
        private Boolean prerelease;
        private Boolean draft;
        @JsonProperty("html_url")
        private String htmlUrl;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawGitHubCommit {
        private String sha;
        private CommitDetail commit;

        @Getter @Setter @NoArgsConstructor @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class CommitDetail {
            private Author author;
            private String message;
        }

        @Getter @Setter @NoArgsConstructor @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Author {
            private String name;
            private String date;
        }
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawGitHubPull {
        private Long id;
        private Integer number;
        private String title;
        @JsonProperty("created_at")
        private String createdAt;
        @JsonProperty("closed_at")
        private String closedAt;
        @JsonProperty("merged_at")
        private String mergedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawGitHubIssue {
        private Long id;
        private Integer number;
        private String title;
        @JsonProperty("created_at")
        private String createdAt;
        @JsonProperty("closed_at")
        private String closedAt;
        @JsonProperty("pull_request")
        private Object pullRequest;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawGitHubSearchResult {
        @JsonProperty("total_count")
        private Integer totalCount;
        private List<RawGitHubRepo> items;
    }
}
