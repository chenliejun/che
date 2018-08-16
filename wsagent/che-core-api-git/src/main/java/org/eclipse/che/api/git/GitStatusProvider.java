/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.git;

import static java.nio.file.Files.getLastModifiedTime;
import static java.util.Collections.singletonList;
import static org.eclipse.che.api.fs.server.WsPathUtils.SEPARATOR;
import static org.eclipse.che.api.fs.server.WsPathUtils.absolutize;
import static org.eclipse.che.api.fs.server.WsPathUtils.resolve;
import static org.eclipse.che.api.project.server.VcsStatusProvider.VcsStatus.MODIFIED;
import static org.eclipse.che.api.project.server.VcsStatusProvider.VcsStatus.NOT_MODIFIED;
import static org.eclipse.che.api.project.server.VcsStatusProvider.VcsStatus.UNTRACKED;
import static org.eclipse.che.dto.server.DtoFactory.newDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.workspace.config.ProjectConfig;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.fs.server.PathTransformer;
import org.eclipse.che.api.git.exception.GitException;
import org.eclipse.che.api.git.shared.FileChangedEventDto;
import org.eclipse.che.api.git.shared.Status;
import org.eclipse.che.api.git.shared.StatusChangedEventDto;
import org.eclipse.che.api.project.server.ProjectManager;
import org.eclipse.che.api.project.server.VcsStatusProvider;
import org.eclipse.che.api.project.server.impl.RootDirPathProvider;
import org.eclipse.che.api.project.server.notification.ProjectCreatedEvent;
import org.eclipse.che.api.project.server.notification.ProjectDeletedEvent;

/**
 * Git implementation of {@link VcsStatusProvider}.
 *
 * @author Igor Vinokur
 */
public class GitStatusProvider implements VcsStatusProvider {

  private final GitConnectionFactory gitConnectionFactory;
  private final PathTransformer pathTransformer;
  private final ProjectManager projectManager;
  private final RootDirPathProvider rootDirPathProvider;
  private final Map<String, Status> statusCache;
  private final Map<String, FileTime> files;

  @Inject
  public GitStatusProvider(
      GitConnectionFactory gitConnectionFactory,
      PathTransformer pathTransformer,
      ProjectManager projectManager,
      RootDirPathProvider rootDirPathProvider,
      EventService eventService) {
    this.gitConnectionFactory = gitConnectionFactory;
    this.pathTransformer = pathTransformer;
    this.projectManager = projectManager;
    this.rootDirPathProvider = rootDirPathProvider;
    this.statusCache = new HashMap<>();
    this.files = new HashMap<>();

    eventService.subscribe(
        event -> {
          statusCache.put(event.getProjectName(), event.getStatus());
        },
        StatusChangedEventDto.class);

    eventService.subscribe(
        event -> {
          FileChangedEventDto.Status status = event.getStatus();
          Status statusDto = newDto(Status.class);
          if (status == FileChangedEventDto.Status.ADDED) {
            statusDto.setAdded(singletonList(event.getPath()));
          } else if (status == FileChangedEventDto.Status.MODIFIED) {
            statusDto.setModified(singletonList(event.getPath()));
          } else if (status == FileChangedEventDto.Status.UNTRACKED) {
            statusDto.setModified(singletonList(event.getPath()));
          }

          String projectName = Paths.get(event.getPath()).getParent().getFileName().toString();
          statusCache.put(projectName, mergeStatus(projectName, statusDto));

          String filePath = rootDirPathProvider.get() + event.getPath();
          try {
            files.put(filePath, getLastModifiedTime(Paths.get(filePath)));
          } catch (IOException e) {
            e.printStackTrace();
          }
        },
        FileChangedEventDto.class);

    eventService.subscribe(
        event -> {
          String projectFsPath = pathTransformer.transform(event.getProjectPath()).toString();
          try {
            Set<Path> paths =
                Files.walk(Paths.get(projectFsPath))
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toSet());
            for (Path path : paths) {
              String filePath = path.toString();
              files.put(filePath, getLastModifiedTime(Paths.get(filePath)));
            }
            statusCache.put(
                event.getProjectPath().substring(event.getProjectPath().lastIndexOf('/') + 1),
                gitConnectionFactory.getConnection(projectFsPath).status(null));
          } catch (GitException | IOException e) {
            e.printStackTrace();
          }
        },
        ProjectCreatedEvent.class);

    eventService.subscribe(
        event -> {
          String projectFsPath = pathTransformer.transform(event.getProjectPath()).toString();
          files.keySet().removeIf(file -> file.startsWith(projectFsPath));
          statusCache.remove(
              event.getProjectPath().substring(event.getProjectPath().lastIndexOf('/') + 1));
        },
        ProjectDeletedEvent.class);

    try {
      Set<Path> collect =
          Files.walk(Paths.get(rootDirPathProvider.get()))
              .filter(Files::isRegularFile)
              .collect(Collectors.toSet());
      for (Path path : collect) {
        String filePath = path.toString();
        files.put(filePath, getLastModifiedTime(Paths.get(filePath)));
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public String getVcsName() {
    return GitProjectType.TYPE_ID;
  }

  @Override
  public VcsStatus getStatus(String wsPath) throws ServerException {
    try {
      ProjectConfig project =
          projectManager
              .getClosest(wsPath)
              .orElseThrow(() -> new NotFoundException("Can't find project"));
      String projectFsPath = pathTransformer.transform(project.getPath()).toString();
      wsPath = wsPath.substring(wsPath.startsWith(SEPARATOR) ? 1 : 0);
      String itemPath = wsPath.substring(wsPath.indexOf(SEPARATOR) + 1);
      Status cachedStatus = statusCache.get(project.getName());
      Status status =
          gitConnectionFactory.getConnection(projectFsPath).status(singletonList(itemPath));
      if (cachedStatus == null) {
        cachedStatus = status;
      }
      if (status.getUntracked().contains(itemPath)) {
        cachedStatus.getUntracked().add(itemPath);
        return UNTRACKED;
      } else if (status.getAdded().contains(itemPath)) {
        cachedStatus.getAdded().add(itemPath);
        return VcsStatus.ADDED;
      } else if (status.getModified().contains(itemPath)) {
        cachedStatus.getModified().add(itemPath);
        return MODIFIED;
      } else if (status.getChanged().contains(itemPath)) {
        cachedStatus.getChanged().add(itemPath);
        return MODIFIED;
      } else {
        return NOT_MODIFIED;
      }
    } catch (GitException | NotFoundException e) {
      throw new ServerException(e.getMessage());
    }
  }

  @Override
  public Map<String, VcsStatus> getStatus(String wsPath, List<String> paths)
      throws ServerException {
    Map<String, VcsStatus> result = new HashMap<>();
    try {
      ProjectConfig project =
          projectManager
              .getClosest(absolutize(wsPath))
              .orElseThrow(() -> new NotFoundException("Can't find project"));
      String projectName = project.getName();
      String projectFsPath = pathTransformer.transform(project.getPath()).toString();
      if (statusCache.get(projectName) == null) {
        statusCache.put(
            projectName, gitConnectionFactory.getConnection(projectFsPath).status(null));
      }

      boolean hasChanged = false;

      for (String path : paths) {
        String itemWsPath = resolve(project.getPath(), path);
        FileTime lastFileTime = files.get(rootDirPathProvider.get() + itemWsPath);
        try {
          FileTime currentFileTime =
              getLastModifiedTime(Paths.get(rootDirPathProvider.get() + itemWsPath));
          if (lastFileTime == null || !lastFileTime.equals(currentFileTime)) {
            files.put(rootDirPathProvider.get() + itemWsPath, currentFileTime);
            hasChanged = true;
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }

      Status status;
      if (hasChanged) {
        remove(projectName, paths);
        status =
            mergeStatus(
                projectName, gitConnectionFactory.getConnection(projectFsPath).status(paths));
        statusCache.put(projectName, status);
      } else {
        status = statusCache.get(projectName);
      }

      paths.forEach(
          path -> {
            String itemWsPath = resolve(project.getPath(), path);
            if (status.getUntracked().contains(path)) {
              result.put(itemWsPath, UNTRACKED);
            } else if (status.getAdded().contains(path)) {
              result.put(itemWsPath, VcsStatus.ADDED);
            } else if (status.getModified().contains(path) || status.getChanged().contains(path)) {
              result.put(itemWsPath, MODIFIED);
            } else {
              result.put(itemWsPath, NOT_MODIFIED);
            }
          });

    } catch (NotFoundException e) {
      throw new ServerException(e.getMessage());
    }
    return result;
  }

  private Status mergeStatus(String project, Status newStatus) {
    Status status = statusCache.get(project);

    newStatus
        .getAdded()
        .forEach(
            added -> {
              status.getAdded().add(added);
            });
    newStatus
        .getChanged()
        .forEach(
            changed -> {
              status.getChanged().add(changed);
            });
    newStatus
        .getModified()
        .forEach(
            modified -> {
              status.getModified().add(modified);
            });
    newStatus
        .getUntracked()
        .forEach(
            untracked -> {
              status.getUntracked().add(untracked);
            });
    newStatus
        .getMissing()
        .forEach(
            missing -> {
              status.getMissing().add(missing);
            });
    newStatus
        .getRemoved()
        .forEach(
            removed -> {
              status.getRemoved().add(removed);
            });
    newStatus
        .getConflicting()
        .forEach(
            conflicting -> {
              status.getConflicting().add(conflicting);
            });
    newStatus
        .getUntrackedFolders()
        .forEach(
            untrackedFolders -> {
              status.getUntrackedFolders().add(untrackedFolders);
            });

    return status;
  }

  private void remove(String project, List<String> paths) {
    Status status = statusCache.get(project);

    paths.forEach(
        path -> {
          status.getAdded().remove(path);
          status.getChanged().remove(path);
          status.getModified().remove(path);
          status.getUntracked().remove(path);
          status.getMissing().remove(path);
          status.getRemoved().remove(path);
          status.getConflicting().remove(path);
          status.getUntrackedFolders().remove(path);
        });
  }
}
