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

package org.apache.hadoop.yarn.server.resourcemanager.scheduler.activities;

import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.yarn.api.records.NodeId;

import java.util.LinkedList;
import java.util.List;

/*
 * It represents tree node in "NodeAllocation" tree structure.
 * Each node may represent queue, application or container in allocation activity.
 * Node may have children node if successfully allocated to next level.
 */
public class ActivityNode {
  private String activityNodeName;
  private String parentName;
  private String appPriority;
  private String requestPriority;
  private ActivityState state;
  private String diagnostic;
  private NodeId nodeId;
  private String allocationRequestId;

  private List<ActivityNode> childNode;

  public ActivityNode(String activityNodeName, String parentName,
      String priority, ActivityState state, String diagnostic, String type) {
    this(activityNodeName, parentName, priority, state, diagnostic, type, null,
        null);
  }

  public ActivityNode(String activityNodeName, String parentName,
      String priority, ActivityState state, String diagnostic, String type,
      NodeId nodeId, String allocationRequestId) {
    this.activityNodeName = activityNodeName;
    this.parentName = parentName;
    if (type != null) {
      if (type.equals("app")) {
        this.appPriority = priority;
      } else if (type.equals("request")) {
        this.requestPriority = priority;
        this.allocationRequestId = allocationRequestId;
      } else if (type.equals("container")) {
        this.requestPriority = priority;
        this.allocationRequestId = allocationRequestId;
        this.nodeId = nodeId;
      }
    }
    this.state = state;
    this.diagnostic = diagnostic;
    this.childNode = new LinkedList<>();
  }

  public String getName() {
    return this.activityNodeName;
  }

  public String getParentName() {
    return this.parentName;
  }

  public void addChild(ActivityNode node) {
    childNode.add(0, node);
  }

  public List<ActivityNode> getChildren() {
    return this.childNode;
  }

  public ActivityState getState() {
    return this.state;
  }

  public String getDiagnostic() {
    return this.diagnostic;
  }

  public String getAppPriority() {
    return appPriority;
  }

  public String getRequestPriority() {
    return requestPriority;
  }

  public NodeId getNodeId() {
    return nodeId;
  }

  public String getAllocationRequestId() {
    return allocationRequestId;
  }

  public boolean isAppType() {
    if (appPriority != null) {
      return true;
    } else {
      return false;
    }
  }

  public boolean isRequestType() {
    return requestPriority != null && nodeId == null;
  }

  public String getShortDiagnostic() {
    if (this.diagnostic == null) {
      return "";
    } else {
      return StringUtils.split(this.diagnostic,
          ActivitiesManager.DIAGNOSTICS_DETAILS_SEPARATOR)[0];
    }
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(this.activityNodeName + " ")
        .append(this.appPriority + " ")
        .append(this.state + " ");
    if (this.nodeId != null) {
      sb.append(this.nodeId + " ");
    }
    if (!this.diagnostic.equals("")) {
      sb.append(this.diagnostic + "\n");
    }
    sb.append("\n");
    for (ActivityNode child : childNode) {
      sb.append(child.toString() + "\n");
    }
    return sb.toString();
  }

}
