// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.copybara.RepoException;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.util.console.Console;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Git repository type. Knowing the repository type allow us to provide better experience, like
 * allowing to import Github PR/Gerrit changes using the web url as the reference.
 */
public enum GitRepoType {

  @DocField(description = "A standard git repository. This is the default")
  GIT {
    /**
     * Standard git resolution tries to apply some heuristics for resolving the ref. Currently it
     * supports:
     * <ul>
     * <li>SHA-1 references if they are reachable from heads. Example:
     * 7d45b192cf1bf3a45990afeb840a382760111dee</li>
     * <li>Valid git refs for {@code repoUrl}. Examples: master, refs/some/ref, etc.</li>
     * <li>Fetching HEAD from a git url. Example http://somerepo.com/foo, file:///home/john/repo,
     * etc. </li>
     * <li>Fetching a reference from a git url. Example: "http://somerepo.com/foo branch"</li>
     * </ul>
     */
    @Override
    GitReference resolveRef(GitRepository repository, String repoUrl, String ref, Console console)
        throws RepoException {
      logger.log(Level.INFO, "Resolving " + repoUrl + " reference: " + ref);
      if (!GIT_URL.matcher(ref).matches() && !FILE_URL.matcher(ref).matches()) {
        // If ref is not an url try a normal fetch of repoUrl and ref
        return repository.fetch(repoUrl, ref);
      }
      String msg = "Git origin URL overwritten in the command line as " + ref;
      console.warn(msg);
      logger.warning(msg + ". Config value was: " + repoUrl);
      console.progress("Fetching HEAD for " + ref);
      GitReference ghPullRequest = maybeFetchGithubPullRequest(repository, ref);
      if (ghPullRequest != null) {
        return ghPullRequest;
      }
      int spaceIdx = ref.lastIndexOf(" ");

      // Treat "http://someurl ref" as a url and a reference. This
      if (spaceIdx != -1) {
        return repository.fetch(ref.substring(0, spaceIdx), ref.substring(spaceIdx + 1));
      }
      return repository.fetch(ref, "HEAD");
    }
  },
  @DocField(description = "A git repository hosted in Github")
  GITHUB {
    /**
     * Github resolution supports all {@link GitRepoType#GIT} formats and additionally if the ref
     * is a github url, is equals to {@code repoUrl} and is a pull request it tries to transform
     * it to a valid git fetch of the equivalent ref.
     */
    @Override
    GitReference resolveRef(GitRepository repository, String repoUrl, String ref, Console console)
        throws RepoException {
      if (ref.startsWith("https://github.com") && ref.startsWith(repoUrl)) {
        GitReference ghPullRequest = maybeFetchGithubPullRequest(repository, ref);
        if (ghPullRequest != null) {
          return ghPullRequest;
        }
      }
      return GIT.resolveRef(repository, repoUrl, ref, console);
    }
  },
  @DocField(description = "A Gerrit code review repository")
  GERRIT {
    @Override
    GitReference resolveRef(GitRepository repository, String repoUrl, String ref, Console console)
        throws RepoException {
      // TODO(malcon): if ref is gerrit url, resolve it properly
      return GIT.resolveRef(repository, repoUrl, ref, console);
    }
  };

  /**
   * Check if the reference is a github pull request url. And if so, fetch it and return the
   * reference. Otherwise return null.
   */
  @Nullable
  static protected GitReference maybeFetchGithubPullRequest(GitRepository repository, String ref)
      throws RepoException {
    Matcher matcher = GITHUB_PULL_REQUEST.matcher(ref);
    if (matcher.matches()) {
      return repository.fetch(matcher.group("url"), "refs" + matcher.group("pull") + "/head");
    }
    return null;
  }

  private static final Logger logger = Logger.getLogger(GitRepoType.class.getCanonicalName());

  private static final Pattern GITHUB_PULL_REQUEST = Pattern.compile(
      "(?<url>https://github[.]com/.+)(?<pull>/pull/[0-9]+)");

  private static final Pattern GIT_URL =
      Pattern.compile("(\\w+://)(.+@)*([\\w.]+)(:[\\d]+){0,1}/*(.*)");

  private static final Pattern FILE_URL = Pattern.compile("file://(.*)");

  abstract GitReference resolveRef(GitRepository repository, String repoUrl, String ref,
      Console console) throws RepoException;
}
