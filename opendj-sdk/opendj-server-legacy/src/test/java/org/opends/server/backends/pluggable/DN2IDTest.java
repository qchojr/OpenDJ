/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.PromiseImpl;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.BackendIndexCfgDefn.IndexType;
import org.opends.server.admin.std.server.BackendIndexCfg;
import org.opends.server.admin.std.server.PersistitBackendCfg;
import org.opends.server.backends.persistit.PersistItStorage;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.SequentialCursor;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.MemoryQuota;
import org.opends.server.core.ServerContext;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = { "precommit", "pluggablebackend" }, sequential = true)
public class DN2IDTest extends DirectoryServerTestCase
{
  private final TreeName dn2IDTreeName = new TreeName("base-dn", "index-id");
  private DN baseDN;
  private DN2ID dn2ID;
  private PersistItStorage storage;

  @BeforeClass
  public void startFakeServer() throws Exception
  {
    TestCaseUtils.startFakeServer();
  }

  @AfterClass
  public void stopFakeServer() throws Exception
  {
    TestCaseUtils.shutdownFakeServer();
  }

  @BeforeMethod
  public void setUp() throws Exception
  {
    ServerContext serverContext = mock(ServerContext.class);
    when(serverContext.getMemoryQuota()).thenReturn(new MemoryQuota());
    when(serverContext.getDiskSpaceMonitor()).thenReturn(mock(DiskSpaceMonitor.class));

    storage = new PersistItStorage(createBackendCfg(), serverContext);
    try(final org.opends.server.backends.pluggable.spi.Importer importer = storage.startImport()) {
      importer.createTree(dn2IDTreeName);
    }

    storage.open();

    baseDN = dn("dc=example, dc=com");
    dn2ID = new DN2ID(dn2IDTreeName, baseDN);
  }

  @AfterMethod
  public void tearDown()
  {
    storage.close();
    storage.removeStorageFiles();
  }

  private void populate() throws DirectoryException, Exception
  {
    final String[] dns =
      {
                                 "dc=example,dc=com",
                      "ou=Devices,dc=example,dc=com",
              "cn=dev0,ou=Devices,dc=example,dc=com",
                       "ou=People,dc=example,dc=com",
                "cn=foo,ou=People,dc=example,dc=com",
             "cn=barbar,ou=People,dc=example,dc=com",
             "cn=foofoo,ou=People,dc=example,dc=com",
                "cn=bar,ou=People,dc=example,dc=com",
        "cn=dev0,cn=bar,ou=People,dc=example,dc=com",
        "cn=dev1,cn=bar,ou=People,dc=example,dc=com"
      };

    for (int i = 0; i < dns.length; i++)
    {
      put(dn(dns[i]), i + 1);
    }
  }

  @Test
  public void testCanAddDN() throws Exception
  {
    populate();

    assertThat(get("dc=example,dc=com")).isEqualTo(id(1));
    assertThat(get("ou=People,dc=example,dc=com")).isEqualTo(id(4));
    assertThat(get("cn=dev1,cn=bar,ou=People,dc=example,dc=com")).isEqualTo(id(10));
  }

  @Test
  public void testGetNonExistingDNReturnNull() throws Exception
  {
    assertThat(get("dc=non,dc=existing")).isNull();
  }

  @Test
  public void testCanRemove() throws Exception
  {
    populate();

    assertThat(get("ou=People,dc=example,dc=com")).isNotNull();
    assertThat(remove("ou=People,dc=example,dc=com")).isTrue();
    assertThat(get("ou=People,dc=example,dc=com")).isNull();
  }

  @Test
  public void testRemoveNonExistingEntry() throws Exception
  {
    assertThat(remove("dc=non,dc=existing")).isFalse();
  }

  @Test
  public void testTraverseChildren() throws Exception
  {
    populate();
    assertThat(traverseChildren("ou=People,dc=example,dc=com"))
      .containsExactly(
                       get("cn=bar,ou=People,dc=example,dc=com"),
                    get("cn=barbar,ou=People,dc=example,dc=com"),
                       get("cn=foo,ou=People,dc=example,dc=com"),
                    get("cn=foofoo,ou=People,dc=example,dc=com"));
  }

  @Test
  public void testTraverseSubordinates() throws Exception
  {
    populate();
    assertThat(traverseSubordinates("ou=People,dc=example,dc=com"))
      .containsExactly(
                      get("cn=bar,ou=People,dc=example,dc=com"),
              get("cn=dev0,cn=bar,ou=People,dc=example,dc=com"),
              get("cn=dev1,cn=bar,ou=People,dc=example,dc=com"),
                   get("cn=barbar,ou=People,dc=example,dc=com"),
                      get("cn=foo,ou=People,dc=example,dc=com"),
                   get("cn=foofoo,ou=People,dc=example,dc=com"));
  }

  private EntryID get(final String dn) throws Exception
  {
    return storage.read(new ReadOperation<EntryID>()
    {
      @Override
      public EntryID run(ReadableTransaction txn) throws Exception
      {
        return dn2ID.get(txn, dn(dn));
      }
    });
  }

  private List<EntryID> traverseChildren(final String dn) throws Exception
  {
    return storage.read(new ReadOperation<List<EntryID>>()
    {
      @Override
      public List<EntryID> run(ReadableTransaction txn) throws Exception
      {
        try (final SequentialCursor<Void, EntryID> cursor = dn2ID.openChildrenCursor(txn, dn(dn)))
        {
          return getAllIDs(cursor);
        }
      }
    });
  }

  private List<EntryID> traverseSubordinates(final String dn) throws Exception
  {
    return storage.read(new ReadOperation<List<EntryID>>()
    {
      @Override
      public List<EntryID> run(ReadableTransaction txn) throws Exception
      {
        try (final SequentialCursor<Void, EntryID> cursor = dn2ID.openSubordinatesCursor(txn, dn(dn)))
        {
          return getAllIDs(cursor);
        }
      }
    });
  }

  private static <K, V> List<V> getAllIDs(SequentialCursor<K, V> cursor) {
    final List<V> values = new ArrayList<>();
    while(cursor.next()) {
      values.add(cursor.getValue());
    }
    return values;
  }

  private void put(final DN dn, final long id) throws Exception
  {
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        dn2ID.put(txn, dn, new EntryID(id));
      }
    });
  }

  private boolean remove(final String dn) throws Exception
  {
    final PromiseImpl<Boolean, NeverThrowsException> p = PromiseImpl.create();
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        p.handleResult(dn2ID.remove(txn, dn(dn)));
      }
    });
    return p.get(10, TimeUnit.SECONDS);
  }

  private static DN dn(String dn) throws DirectoryException
  {
    return DN.valueOf(dn);
  }

  private static EntryID id(long id)
  {
    return new EntryID(id);
  }

  private static PersistitBackendCfg createBackendCfg() throws ConfigException, DirectoryException
  {
    String homeDirName = "pdb_test";
    PersistitBackendCfg backendCfg = mock(PersistitBackendCfg.class);

    when(backendCfg.getBackendId()).thenReturn("persTest" + homeDirName);
    when(backendCfg.getDBDirectory()).thenReturn(homeDirName);
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(0L);
    when(backendCfg.getDBCachePercent()).thenReturn(20);
    when(backendCfg.getBaseDN()).thenReturn(TestCaseUtils.newSortedSet(DN.valueOf("dc=test,dc=com")));
    when(backendCfg.dn()).thenReturn(DN.valueOf("dc=test,dc=com"));
    when(backendCfg.listBackendIndexes()).thenReturn(new String[] { "sn" });
    when(backendCfg.listBackendVLVIndexes()).thenReturn(new String[0]);

    BackendIndexCfg indexCfg = mock(BackendIndexCfg.class);
    when(indexCfg.getIndexType()).thenReturn(TestCaseUtils.newSortedSet(IndexType.PRESENCE, IndexType.EQUALITY));
    when(indexCfg.getAttribute()).thenReturn(DirectoryServer.getAttributeType("sn"));
    when(backendCfg.getBackendIndex("sn")).thenReturn(indexCfg);

    return backendCfg;
  }

}
