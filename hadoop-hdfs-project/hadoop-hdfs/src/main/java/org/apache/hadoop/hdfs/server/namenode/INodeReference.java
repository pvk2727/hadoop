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
package org.apache.hadoop.hdfs.server.namenode;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.fs.permission.PermissionStatus;
import org.apache.hadoop.hdfs.protocol.QuotaExceededException;
import org.apache.hadoop.hdfs.server.namenode.snapshot.FileWithSnapshot;
import org.apache.hadoop.hdfs.server.namenode.snapshot.INodeDirectoryWithSnapshot;
import org.apache.hadoop.hdfs.server.namenode.snapshot.Snapshot;

import com.google.common.base.Preconditions;

/**
 * An anonymous reference to an inode.
 *
 * This class and its subclasses are used to support multiple access paths.
 * A file/directory may have multiple access paths when it is stored in some
 * snapshots and it is renamed/moved to other locations.
 * 
 * For example,
 * (1) Support we have /abc/foo, say the inode of foo is inode(id=1000,name=foo)
 * (2) create snapshot s0 for /abc
 * (3) mv /abc/foo /xyz/bar, i.e. inode(id=1000,name=...) is renamed from "foo"
 *     to "bar" and its parent becomes /xyz.
 * 
 * Then, /xyz/bar and /abc/.snapshot/s0/foo are two different access paths to
 * the same inode, inode(id=1000,name=bar).
 *
 * With references, we have the following
 * - /abc has a child ref(id=1001,name=foo).
 * - /xyz has a child ref(id=1002) 
 * - Both ref(id=1001,name=foo) and ref(id=1002) point to another reference,
 *   ref(id=1003,count=2).
 * - Finally, ref(id=1003,count=2) points to inode(id=1000,name=bar).
 * 
 * Note 1: For a reference without name, e.g. ref(id=1002), it uses the name
 *         of the referred inode.
 * Note 2: getParent() always returns the parent in the current state, e.g.
 *         inode(id=1000,name=bar).getParent() returns /xyz but not /abc.
 */
public abstract class INodeReference extends INode {
  /**
   * Try to remove the given reference and then return the reference count.
   * If the given inode is not a reference, return -1;
   */
  public static int tryRemoveReference(INode inode) {
    if (!inode.isReference()) {
      return -1;
    }
    return removeReference(inode.asReference());
  }

  /**
   * Remove the given reference and then return the reference count.
   * If the referred inode is not a WithCount, return -1;
   */
  private static int removeReference(INodeReference ref) {
    final INode referred = ref.getReferredINode();
    if (!(referred instanceof WithCount)) {
      return -1;
    }
    
    WithCount wc = (WithCount) referred;
    wc.removeReference(ref);
    return wc.getReferenceCount();
  }

  /**
   * When destroying a reference node (WithName or DstReference), we call this
   * method to identify the snapshot which is the latest snapshot before the
   * reference node's creation. 
   */
  static Snapshot getPriorSnapshot(INodeReference ref) {
    WithCount wc = (WithCount) ref.getReferredINode();
    WithName wn = null;
    if (ref instanceof DstReference) {
      wn = wc.getLastWithName();
    } else if (ref instanceof WithName) {
      wn = wc.getPriorWithName((WithName) ref);
    }
    if (wn != null) {
      INode referred = wc.getReferredINode();
      if (referred instanceof FileWithSnapshot) {
        return ((FileWithSnapshot) referred).getDiffs().getPrior(
            wn.lastSnapshotId);
      } else if (referred instanceof INodeDirectoryWithSnapshot) { 
        return ((INodeDirectoryWithSnapshot) referred).getDiffs().getPrior(
            wn.lastSnapshotId);
      }
    }
    return null;
  }
  
  private INode referred;
  
  public INodeReference(INode parent, INode referred) {
    super(parent);
    this.referred = referred;
  }

  public final INode getReferredINode() {
    return referred;
  }

  public final void setReferredINode(INode referred) {
    this.referred = referred;
  }
  
  @Override
  public final boolean isReference() {
    return true;
  }
  
  @Override
  public final INodeReference asReference() {
    return this;
  }

  @Override
  public final boolean isFile() {
    return referred.isFile();
  }
  
  @Override
  public final INodeFile asFile() {
    return referred.asFile();
  }
  
  @Override
  public final boolean isDirectory() {
    return referred.isDirectory();
  }
  
  @Override
  public final INodeDirectory asDirectory() {
    return referred.asDirectory();
  }
  
  @Override
  public final boolean isSymlink() {
    return referred.isSymlink();
  }
  
  @Override
  public final INodeSymlink asSymlink() {
    return referred.asSymlink();
  }

  @Override
  public byte[] getLocalNameBytes() {
    return referred.getLocalNameBytes();
  }

  @Override
  public void setLocalName(byte[] name) {
    referred.setLocalName(name);
  }

  @Override
  public final long getId() {
    return referred.getId();
  }
  
  @Override
  public final PermissionStatus getPermissionStatus(Snapshot snapshot) {
    return referred.getPermissionStatus(snapshot);
  }
  
  @Override
  public final String getUserName(Snapshot snapshot) {
    return referred.getUserName(snapshot);
  }
  
  @Override
  final void setUser(String user) {
    referred.setUser(user);
  }
  
  @Override
  public final String getGroupName(Snapshot snapshot) {
    return referred.getGroupName(snapshot);
  }
  
  @Override
  final void setGroup(String group) {
    referred.setGroup(group);
  }
  
  @Override
  public final FsPermission getFsPermission(Snapshot snapshot) {
    return referred.getFsPermission(snapshot);
  }
  @Override
  public final short getFsPermissionShort() {
    return referred.getFsPermissionShort();
  }
  
  @Override
  void setPermission(FsPermission permission) {
    referred.setPermission(permission);
  }

  @Override
  public long getPermissionLong() {
    return referred.getPermissionLong();
  }

  @Override
  public final long getModificationTime(Snapshot snapshot) {
    return referred.getModificationTime(snapshot);
  }
  
  @Override
  public final INode updateModificationTime(long mtime, Snapshot latest,
      INodeMap inodeMap) throws QuotaExceededException {
    return referred.updateModificationTime(mtime, latest, inodeMap);
  }
  
  @Override
  public final void setModificationTime(long modificationTime) {
    referred.setModificationTime(modificationTime);
  }
  
  @Override
  public final long getAccessTime(Snapshot snapshot) {
    return referred.getAccessTime(snapshot);
  }
  
  @Override
  public final void setAccessTime(long accessTime) {
    referred.setAccessTime(accessTime);
  }

  @Override
  final INode recordModification(Snapshot latest, final INodeMap inodeMap)
      throws QuotaExceededException {
    referred.recordModification(latest, inodeMap);
    // reference is never replaced 
    return this;
  }

  @Override // used by WithCount
  public Quota.Counts cleanSubtree(Snapshot snapshot, Snapshot prior,
      BlocksMapUpdateInfo collectedBlocks, final List<INode> removedINodes,
      final boolean countDiffChange) throws QuotaExceededException {
    return referred.cleanSubtree(snapshot, prior, collectedBlocks,
        removedINodes, countDiffChange);
  }

  @Override // used by WithCount
  public void destroyAndCollectBlocks(
      BlocksMapUpdateInfo collectedBlocks, final List<INode> removedINodes) {
    if (removeReference(this) <= 0) {
      referred.destroyAndCollectBlocks(collectedBlocks, removedINodes);
    }
  }

  @Override
  public ContentSummaryComputationContext computeContentSummary(
      ContentSummaryComputationContext summary) {
    return referred.computeContentSummary(summary);
  }

  @Override
  public Quota.Counts computeQuotaUsage(Quota.Counts counts, boolean useCache,
      int lastSnapshotId) {
    return referred.computeQuotaUsage(counts, useCache, lastSnapshotId);
  }
  
  @Override
  public final INodeAttributes getSnapshotINode(Snapshot snapshot) {
    return referred.getSnapshotINode(snapshot);
  }

  @Override
  public final long getNsQuota() {
    return referred.getNsQuota();
  }

  @Override
  public final long getDsQuota() {
    return referred.getDsQuota();
  }
  
  @Override
  public final void clear() {
    super.clear();
    referred = null;
  }

  @Override
  public void dumpTreeRecursively(PrintWriter out, StringBuilder prefix,
      final Snapshot snapshot) {
    super.dumpTreeRecursively(out, prefix, snapshot);
    if (this instanceof DstReference) {
      out.print(", dstSnapshotId=" + ((DstReference) this).dstSnapshotId);
    }
    if (this instanceof WithCount) {
      out.print(", count=" + ((WithCount)this).getReferenceCount());
    }
    out.println();
    
    final StringBuilder b = new StringBuilder();
    for(int i = 0; i < prefix.length(); i++) {
      b.append(' ');
    }
    b.append("->");
    getReferredINode().dumpTreeRecursively(out, b, snapshot);
  }
  
  public int getDstSnapshotId() {
    return Snapshot.INVALID_ID;
  }
  
  /** An anonymous reference with reference count. */
  public static class WithCount extends INodeReference {
    
    private final List<WithName> withNameList = new ArrayList<WithName>();
    
    /**
     * Compare snapshot with IDs, where null indicates the current status thus
     * is greater than any non-null snapshot.
     */
    public static final Comparator<WithName> WITHNAME_COMPARATOR
        = new Comparator<WithName>() {
      @Override
      public int compare(WithName left, WithName right) {
        return left.lastSnapshotId - right.lastSnapshotId;
      }
    };
    
    public WithCount(INodeReference parent, INode referred) {
      super(parent, referred);
      Preconditions.checkArgument(!referred.isReference());
      referred.setParentReference(this);
    }
    
    public int getReferenceCount() {
      int count = withNameList.size();
      if (getParentReference() != null) {
        count++;
      }
      return count;
    }

    /** Increment and then return the reference count. */
    public void addReference(INodeReference ref) {
      if (ref instanceof WithName) {
        WithName refWithName = (WithName) ref;
        int i = Collections.binarySearch(withNameList, refWithName,
            WITHNAME_COMPARATOR);
        Preconditions.checkState(i < 0);
        withNameList.add(-i - 1, refWithName);
      } else if (ref instanceof DstReference) {
        setParentReference(ref);
      }
    }

    /** Decrement and then return the reference count. */
    public void removeReference(INodeReference ref) {
      if (ref instanceof WithName) {
        int i = Collections.binarySearch(withNameList, (WithName) ref,
            WITHNAME_COMPARATOR);
        if (i >= 0) {
          withNameList.remove(i);
        }
      } else if (ref == getParentReference()) {
        setParent(null);
      }
    }
    
    WithName getLastWithName() {
      return withNameList.size() > 0 ? 
          withNameList.get(withNameList.size() - 1) : null;
    }
    
    WithName getPriorWithName(WithName post) {
      int i = Collections.binarySearch(withNameList, post, WITHNAME_COMPARATOR);
      if (i > 0) {
        return withNameList.get(i - 1);
      } else if (i == 0 || i == -1) {
        return null;
      } else {
        return withNameList.get(-i - 2);
      }
    }
  }
  
  /** A reference with a fixed name. */
  public static class WithName extends INodeReference {

    private final byte[] name;

    /**
     * The id of the last snapshot in the src tree when this WithName node was 
     * generated. When calculating the quota usage of the referred node, only 
     * the files/dirs existing when this snapshot was taken will be counted for 
     * this WithName node and propagated along its ancestor path.
     */
    private final int lastSnapshotId;
    
    public WithName(INodeDirectory parent, WithCount referred, byte[] name,
        int lastSnapshotId) {
      super(parent, referred);
      this.name = name;
      this.lastSnapshotId = lastSnapshotId;
      referred.addReference(this);
    }

    @Override
    public final byte[] getLocalNameBytes() {
      return name;
    }

    @Override
    public final void setLocalName(byte[] name) {
      throw new UnsupportedOperationException("Cannot set name: " + getClass()
          + " is immutable.");
    }
    
    public int getLastSnapshotId() {
      return lastSnapshotId;
    }
    
    @Override
    public final ContentSummaryComputationContext computeContentSummary(
        ContentSummaryComputationContext summary) {
      //only count diskspace for WithName
      final Quota.Counts q = Quota.Counts.newInstance();
      computeQuotaUsage(q, false, lastSnapshotId);
      summary.getCounts().add(Content.DISKSPACE, q.get(Quota.DISKSPACE));
      return summary;
    }

    @Override
    public final Quota.Counts computeQuotaUsage(Quota.Counts counts,
        boolean useCache, int lastSnapshotId) {
      // if this.lastSnapshotId < lastSnapshotId, the rename of the referred 
      // node happened before the rename of its ancestor. This should be 
      // impossible since for WithName node we only count its children at the 
      // time of the rename. 
      Preconditions.checkState(this.lastSnapshotId >= lastSnapshotId);
      final INode referred = this.getReferredINode().asReference()
          .getReferredINode();
      // We will continue the quota usage computation using the same snapshot id
      // as time line (if the given snapshot id is valid). Also, we cannot use 
      // cache for the referred node since its cached quota may have already 
      // been updated by changes in the current tree.
      int id = lastSnapshotId > Snapshot.INVALID_ID ? 
          lastSnapshotId : this.lastSnapshotId;
      return referred.computeQuotaUsage(counts, false, id);
    }
    
    @Override
    public Quota.Counts cleanSubtree(final Snapshot snapshot, Snapshot prior,
        final BlocksMapUpdateInfo collectedBlocks,
        final List<INode> removedINodes, final boolean countDiffChange)
        throws QuotaExceededException {
      // since WithName node resides in deleted list acting as a snapshot copy,
      // the parameter snapshot must be non-null
      Preconditions.checkArgument(snapshot != null);
      // if prior is null, we need to check snapshot belonging to the previous
      // WithName instance
      if (prior == null) {
        prior = getPriorSnapshot(this);
      }
      
      if (prior != null
          && Snapshot.ID_COMPARATOR.compare(snapshot, prior) <= 0) {
        return Quota.Counts.newInstance();
      }

      Quota.Counts counts = getReferredINode().cleanSubtree(snapshot, prior,
          collectedBlocks, removedINodes, false);
      INodeReference ref = getReferredINode().getParentReference();
      if (ref != null) {
        ref.addSpaceConsumed(-counts.get(Quota.NAMESPACE),
            -counts.get(Quota.DISKSPACE), true);
      }
      
      if (snapshot.getId() < lastSnapshotId) {
        // for a WithName node, when we compute its quota usage, we only count
        // in all the nodes existing at the time of the corresponding rename op.
        // Thus if we are deleting a snapshot before/at the snapshot associated 
        // with lastSnapshotId, we do not need to update the quota upwards.
        counts = Quota.Counts.newInstance();
      }
      return counts;
    }
    
    @Override
    public void destroyAndCollectBlocks(BlocksMapUpdateInfo collectedBlocks,
        final List<INode> removedINodes) {
      Snapshot snapshot = getSelfSnapshot();
      if (removeReference(this) <= 0) {
        getReferredINode().destroyAndCollectBlocks(collectedBlocks,
            removedINodes);
      } else {
        Snapshot prior = getPriorSnapshot(this);
        INode referred = getReferredINode().asReference().getReferredINode();
        
        if (snapshot != null) {
          if (prior != null && snapshot.getId() <= prior.getId()) {
            // the snapshot to be deleted has been deleted while traversing 
            // the src tree of the previous rename operation. This usually 
            // happens when rename's src and dst are under the same 
            // snapshottable directory. E.g., the following operation sequence:
            // 1. create snapshot s1 on /test
            // 2. rename /test/foo/bar to /test/foo2/bar
            // 3. create snapshot s2 on /test
            // 4. rename foo2 again
            // 5. delete snapshot s2
            return;
          }
          try {
            Quota.Counts counts = referred.cleanSubtree(snapshot, prior,
                collectedBlocks, removedINodes, false);
            INodeReference ref = getReferredINode().getParentReference();
            if (ref != null) {
              ref.addSpaceConsumed(-counts.get(Quota.NAMESPACE),
                  -counts.get(Quota.DISKSPACE), true);
            }
          } catch (QuotaExceededException e) {
            LOG.error("should not exceed quota while snapshot deletion", e);
          }
        }
      }
    }
    
    private Snapshot getSelfSnapshot() {
      INode referred = getReferredINode().asReference().getReferredINode();
      Snapshot snapshot = null;
      if (referred instanceof FileWithSnapshot) {
        snapshot = ((FileWithSnapshot) referred).getDiffs().getPrior(
            lastSnapshotId);
      } else if (referred instanceof INodeDirectoryWithSnapshot) {
        snapshot = ((INodeDirectoryWithSnapshot) referred).getDiffs().getPrior(
            lastSnapshotId);
      }
      return snapshot;
    }
  }
  
  public static class DstReference extends INodeReference {
    /**
     * Record the latest snapshot of the dst subtree before the rename. For
     * later operations on the moved/renamed files/directories, if the latest
     * snapshot is after this dstSnapshot, changes will be recorded to the
     * latest snapshot. Otherwise changes will be recorded to the snapshot
     * belonging to the src of the rename.
     * 
     * {@link Snapshot#INVALID_ID} means no dstSnapshot (e.g., src of the
     * first-time rename).
     */
    private final int dstSnapshotId;
    
    @Override
    public final int getDstSnapshotId() {
      return dstSnapshotId;
    }
    
    public DstReference(INodeDirectory parent, WithCount referred,
        final int dstSnapshotId) {
      super(parent, referred);
      this.dstSnapshotId = dstSnapshotId;
      referred.addReference(this);
    }
    
    @Override
    public Quota.Counts cleanSubtree(Snapshot snapshot, Snapshot prior,
        BlocksMapUpdateInfo collectedBlocks, List<INode> removedINodes,
        final boolean countDiffChange) throws QuotaExceededException {
      if (snapshot == null && prior == null) {
        Quota.Counts counts = Quota.Counts.newInstance();
        this.computeQuotaUsage(counts, true);
        destroyAndCollectBlocks(collectedBlocks, removedINodes);
        return counts;
      } else {
        // if prior is null, we need to check snapshot belonging to the previous
        // WithName instance
        if (prior == null) {
          prior = getPriorSnapshot(this);
        }
        // if prior is not null, and prior is not before the to-be-deleted 
        // snapshot, we can quit here and leave the snapshot deletion work to 
        // the src tree of rename
        if (snapshot != null && prior != null
            && Snapshot.ID_COMPARATOR.compare(snapshot, prior) <= 0) {
          return Quota.Counts.newInstance();
        }
        return getReferredINode().cleanSubtree(snapshot, prior,
            collectedBlocks, removedINodes, countDiffChange);
      }
    }
    
    /**
     * {@inheritDoc}
     * <br/>
     * To destroy a DstReference node, we first remove its link with the 
     * referred node. If the reference number of the referred node is <= 0, we 
     * destroy the subtree of the referred node. Otherwise, we clean the 
     * referred node's subtree and delete everything created after the last 
     * rename operation, i.e., everything outside of the scope of the prior 
     * WithName nodes.
     */
    @Override
    public void destroyAndCollectBlocks(
        BlocksMapUpdateInfo collectedBlocks, final List<INode> removedINodes) {
      if (removeReference(this) <= 0) {
        getReferredINode().destroyAndCollectBlocks(collectedBlocks,
            removedINodes);
      } else {
        // we will clean everything, including files, directories, and 
        // snapshots, that were created after this prior snapshot
        Snapshot prior = getPriorSnapshot(this);
        // prior must be non-null, otherwise we do not have any previous 
        // WithName nodes, and the reference number will be 0.
        Preconditions.checkState(prior != null);
        // identify the snapshot created after prior
        Snapshot snapshot = getSelfSnapshot(prior);
        
        INode referred = getReferredINode().asReference().getReferredINode();
        if (referred instanceof FileWithSnapshot) {
          // if referred is a file, it must be a FileWithSnapshot since we did
          // recordModification before the rename
          FileWithSnapshot sfile = (FileWithSnapshot) referred;
          // make sure we mark the file as deleted
          sfile.deleteCurrentFile();
          try {
            // when calling cleanSubtree of the referred node, since we 
            // compute quota usage updates before calling this destroy 
            // function, we use true for countDiffChange
            referred.cleanSubtree(snapshot, prior, collectedBlocks,
                removedINodes, true);
          } catch (QuotaExceededException e) {
            LOG.error("should not exceed quota while snapshot deletion", e);
          }
        } else if (referred instanceof INodeDirectoryWithSnapshot) {
          // similarly, if referred is a directory, it must be an
          // INodeDirectoryWithSnapshot
          INodeDirectoryWithSnapshot sdir = 
              (INodeDirectoryWithSnapshot) referred;
          try {
            INodeDirectoryWithSnapshot.destroyDstSubtree(sdir, snapshot, prior,
                collectedBlocks, removedINodes);
          } catch (QuotaExceededException e) {
            LOG.error("should not exceed quota while snapshot deletion", e);
          }
        }
      }
    }
    
    private Snapshot getSelfSnapshot(final Snapshot prior) {
      WithCount wc = (WithCount) getReferredINode().asReference();
      INode referred = wc.getReferredINode();
      Snapshot lastSnapshot = null;
      if (referred instanceof FileWithSnapshot) {
        lastSnapshot = ((FileWithSnapshot) referred).getDiffs()
            .getLastSnapshot(); 
      } else if (referred instanceof INodeDirectoryWithSnapshot) {
        lastSnapshot = ((INodeDirectoryWithSnapshot) referred)
            .getLastSnapshot();
      }
      if (lastSnapshot != null && !lastSnapshot.equals(prior)) {
        return lastSnapshot;
      } else {
        return null;
      }
    }
  }
}
