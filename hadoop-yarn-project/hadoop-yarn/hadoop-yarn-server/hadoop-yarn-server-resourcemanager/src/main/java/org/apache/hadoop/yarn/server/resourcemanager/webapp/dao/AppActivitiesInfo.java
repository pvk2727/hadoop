/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.server.resourcemanager.webapp.dao;

import com.google.common.annotations.VisibleForTesting;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.RMWSConsts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.activities.AppAllocation;
import org.apache.hadoop.yarn.util.SystemClock;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/*
 * DAO object to display application activity.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class AppActivitiesInfo {
  protected String applicationId;
  protected String diagnostic;
  protected String timeStamp;
  protected List<AppAllocationInfo> allocations;

  private static final Logger LOG =
      LoggerFactory.getLogger(AppActivitiesInfo.class);

  public AppActivitiesInfo() {
  }

  public AppActivitiesInfo(String errorMessage, String applicationId) {
    this.diagnostic = errorMessage;
    this.applicationId = applicationId;

    Date date = new Date();
    date.setTime(SystemClock.getInstance().getTime());
    this.timeStamp = date.toString();
  }

  public AppActivitiesInfo(List<AppAllocation> appAllocations,
      ApplicationId applicationId,
      RMWSConsts.ActivitiesGroupBy groupBy) {
    this.applicationId = applicationId.toString();
    this.allocations = new ArrayList<>();

    if (appAllocations == null) {
      diagnostic = "waiting for display";

      Date date = new Date();
      date.setTime(SystemClock.getInstance().getTime());
      this.timeStamp = date.toString();
    } else {
      for (int i = appAllocations.size() - 1; i > -1; i--) {
        AppAllocation appAllocation = appAllocations.get(i);
        AppAllocationInfo appAllocationInfo = new AppAllocationInfo(
            appAllocation, groupBy);
        this.allocations.add(appAllocationInfo);
      }
    }
  }

  @VisibleForTesting
  public List<AppAllocationInfo> getAllocations() {
    return allocations;
  }
}
