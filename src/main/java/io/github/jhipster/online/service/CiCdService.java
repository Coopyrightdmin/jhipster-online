/**
 * Copyright 2017-2018 the original author or authors from the JHipster Online project.
 *
 * This file is part of the JHipster Online project, see https://github.com/jhipster/jhipster-online
 * for more information.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.jhipster.online.service;

import java.io.*;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import io.github.jhipster.online.config.ApplicationProperties;
import io.github.jhipster.online.domain.User;

@Service
public class CiCdService {

    private final Logger log = LoggerFactory.getLogger(CiCdService.class);

    private final LogsService logsService;

    private final GitService gitService;

    private final GithubService githubService;

    private final JHipsterService jHipsterService;

    private final ApplicationProperties applicationProperties;

    public CiCdService(LogsService logsService, GitService gitService,
        GithubService githubService, JHipsterService jHipsterService, ApplicationProperties applicationProperties) {
        this.logsService = logsService;
        this.gitService = gitService;
        this.githubService = githubService;
        this.jHipsterService = jHipsterService;
        this.applicationProperties = applicationProperties;
    }

    /**
     * Apply a JDL Model to an existing repository.
     */
    @Async
    public void configureCiCd(User user, String organizationName, String projectName, String ciCdTool, String ciCdId) {
        StopWatch watch = new StopWatch();
        watch.start();
        try {
            log.info("Beginning to configure CI with {} to {} / {}", ciCdTool, organizationName, projectName);
            this.logsService.addLog(ciCdId, "Cloning GitHub repository `" + organizationName +
                "/" + projectName + "`");
            File workingDir = new File(applicationProperties.getTmpFolder() + "/jhipster/applications/" +
                ciCdId);
            FileUtils.forceMkdir(workingDir);
            Git git  = this.gitService.cloneRepository(user, workingDir, organizationName, projectName);

            String branchName = "jhipster-" + ciCdTool + "-" + ciCdId;
            this.logsService.addLog(ciCdId, "Creating branch `" + branchName + "`");
            this.gitService.createBranch(git, branchName);

            this.logsService.addLog(ciCdId, "Generating Continuous Integration configuration");
            //this.jHipsterService.installYarnDependencies(ciCdId, workingDir);
            this.jHipsterService.addCiCdTravis(ciCdId, workingDir, ciCdTool);

            this.gitService.addAllFilesToRepository(git, workingDir);
            this.gitService.commit(git, workingDir, "Configure " +
                StringUtils.capitalize(ciCdTool) +
                " Continuous Integration");

            this.logsService.addLog(ciCdId, "Pushing the application to the Git remote repository");
            this.gitService.push(git, workingDir, user, organizationName, projectName);
            this.logsService.addLog(ciCdId, "Application successfully pushed!");
            this.logsService.addLog(ciCdId, "Creating Pull Request");

            String pullRequestTitle = "Configure Continuous Integration with " + StringUtils.capitalize(ciCdTool);
            String pullRequestBody = "Continuous Integration configured by JHipster";

            int pullRequestNumber =
                this.githubService.createPullRequest(user, organizationName, projectName, pullRequestTitle,
                    branchName, pullRequestBody);

            this.logsService.addLog(ciCdId, "Pull Request created at https://github.com/" +
                organizationName +
                "/" +
                projectName +
                "/pull/" +
                pullRequestNumber
             );

            this.gitService.cleanUpDirectory(workingDir);

            this.logsService.addLog(ciCdId, "Generation finished");
        } catch (Exception e) {
            this.logsService.addLog(ciCdId, "Error during generation: " + e.getMessage());
            e.printStackTrace();
            this.logsService.addLog(ciCdId, "Generation failed");
        }
        watch.stop();
    }
}
