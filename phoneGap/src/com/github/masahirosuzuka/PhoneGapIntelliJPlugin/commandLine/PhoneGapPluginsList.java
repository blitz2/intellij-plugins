package com.github.masahirosuzuka.PhoneGapIntelliJPlugin.commandLine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.webcore.packaging.InstalledPackage;
import com.intellij.webcore.packaging.RepoPackage;
import gnu.trove.THashMap;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class PhoneGapPluginsList {

  public static final String NPMJS_REGISTRY = "https://registry.npmjs.org/";

  public static String PLUGINS_URL = npmRegistry();

  public static volatile Map<String, PhoneGapRepoPackage> CACHED_REPO;

  private static final Logger LOGGER = Logger.getInstance(PhoneGapPluginsList.class);

  private static final Lock lock = new ReentrantLock();

  public static final class PhoneGapRepoPackage extends RepoPackage {
    private final String myDesc;

    public PhoneGapRepoPackage(String name, JsonObject jsonObject) {
      super(name, PLUGINS_URL, getVersionLatest(jsonObject.getAsJsonObject()));
      myDesc = getDescr(jsonObject);
    }

    private static String getDescr(JsonObject jsonObject) {
      return jsonObject.get("description").getAsString();
    }

    private static String getVersionLatest(JsonObject jsonObject) {
      return jsonObject.get("version").getAsString();
    }

    public String getDesc() {
      return myDesc;
    }
  }

  public static PhoneGapRepoPackage getPackage(String name) {
    return mapCached().get(name);
  }

  public static List<RepoPackage> listCached() {
    return ContainerUtil.newArrayList(mapCached().values());
  }

  public static Map<String, PhoneGapRepoPackage> mapCached() {
    Map<String, PhoneGapRepoPackage> value = CACHED_REPO;
    if (value == null) {
      lock.lock();
      try {
        value = CACHED_REPO;
        if (value == null) {
          value = listNoCache();
          CACHED_REPO = value;
        }
      }
      finally {
        lock.unlock();
      }
    }
    return value;
  }

  private static Map<String, PhoneGapRepoPackage> listNoCache() {
    try {
      Map<String, PhoneGapRepoPackage> result = new THashMap<>();
      List<String> params = new ArrayList<String>();
      params.add("search");
      params.add("--json");
      params.add("-l");
      params.add("ecosystem:cordova");
      String npmCmd = "npm";
      File deployExecutable = PathEnvironmentVariableUtil.findInPath(SystemInfo.isWindows ? npmCmd + ".cmd" : npmCmd);
      if (deployExecutable == null) return result;
      GeneralCommandLine line = new GeneralCommandLine(deployExecutable.getAbsolutePath());
      line.addParameters(params);
      ProcessOutput output = ExecUtil.execAndGetOutput(line);
      if (!StringUtil.isEmpty(output.getStderr())) {
        return result;
      }

      if (output.getExitCode() != 0) return result;

      JsonArray packages = new JsonParser().parse(output.getStdout()).getAsJsonArray();
      for (int i = 0; i < packages.size(); ++i) {
        JsonObject eachPackage = packages.get(i).getAsJsonObject();
        String packageName = eachPackage.get("name").getAsString();
        result.put(packageName, new PhoneGapRepoPackage(packageName, eachPackage));
      }
      return result;
    }
    catch (ExecutionException e) {
      //throw new RuntimeException(e.getMessage(), e);
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(e.getMessage(), e);
      }
      return ContainerUtil.newHashMap();
    }
  }

  public static void resetCache() {
    CACHED_REPO = null;
  }

  public static List<InstalledPackage> wrapInstalled(List<String> names) {
    return ContainerUtil.map(names, s -> {
      String[] split = s.split(" ");
      String name = ArrayUtil.getFirstElement(split);
      String version = split.length > 1 ? split[1] : "";
      return new InstalledPackage(name, version);
    });
  }

  public static List<RepoPackage> wrapRepo(List<String> names) {
    return ContainerUtil.map(names, s -> new RepoPackage(s, s));
  }

  public static String npmRegistry() {
    try {
      String npmCmd = "npm";
      File deployExecutable = PathEnvironmentVariableUtil.findInPath(SystemInfo.isWindows ? npmCmd + ".cmd" : npmCmd);
      if (deployExecutable == null) return NPMJS_REGISTRY;
      GeneralCommandLine line = new GeneralCommandLine(deployExecutable.getAbsolutePath());
      List<String> params = new ArrayList<String>();
      params.add("config");
      params.add("get");
      params.add("registry");
      line.addParameters(params);
      ProcessOutput output = ExecUtil.execAndGetOutput(line);
      if (!StringUtil.isEmpty(output.getStderr())) {
        return NPMJS_REGISTRY;
      }

      if (output.getExitCode() != 0) return NPMJS_REGISTRY;
      return output.getStdout();
    }
    catch (ExecutionException e) {
      //throw new RuntimeException(e.getMessage(), e);
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(e.getMessage(), e);
      }
      return NPMJS_REGISTRY;
    }
  }
}
