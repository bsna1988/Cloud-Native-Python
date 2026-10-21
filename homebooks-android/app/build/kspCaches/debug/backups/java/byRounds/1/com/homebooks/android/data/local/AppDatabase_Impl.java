package com.homebooks.android.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.homebooks.android.data.local.dao.BookDao;
import com.homebooks.android.data.local.dao.BookDao_Impl;
import com.homebooks.android.data.local.dao.PendingMutationDao;
import com.homebooks.android.data.local.dao.PendingMutationDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile BookDao _bookDao;

  private volatile PendingMutationDao _pendingMutationDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `books` (`uuid` TEXT NOT NULL, `serverId` INTEGER, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `barcode` TEXT, `releaseDate` TEXT NOT NULL, `description` TEXT NOT NULL, `thumbnail` TEXT, `imagesJson` TEXT NOT NULL, `syncState` TEXT NOT NULL, `isDeleted` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`uuid`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `pending_mutations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookUuid` TEXT NOT NULL, `operationType` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `localImagePathsJson` TEXT NOT NULL, `removedImagesJson` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '9d7b5e47c78a47703d47390697901194')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `books`");
        db.execSQL("DROP TABLE IF EXISTS `pending_mutations`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsBooks = new HashMap<String, TableInfo.Column>(12);
        _columnsBooks.put("uuid", new TableInfo.Column("uuid", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBooks.put("serverId", new TableInfo.Column("serverId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBooks.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBooks.put("author", new TableInfo.Column("author", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBooks.put("barcode", new TableInfo.Column("barcode", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBooks.put("releaseDate", new TableInfo.Column("releaseDate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBooks.put("description", new TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBooks.put("thumbnail", new TableInfo.Column("thumbnail", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBooks.put("imagesJson", new TableInfo.Column("imagesJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBooks.put("syncState", new TableInfo.Column("syncState", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBooks.put("isDeleted", new TableInfo.Column("isDeleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBooks.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBooks = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBooks = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoBooks = new TableInfo("books", _columnsBooks, _foreignKeysBooks, _indicesBooks);
        final TableInfo _existingBooks = TableInfo.read(db, "books");
        if (!_infoBooks.equals(_existingBooks)) {
          return new RoomOpenHelper.ValidationResult(false, "books(com.homebooks.android.data.local.entity.BookEntity).\n"
                  + " Expected:\n" + _infoBooks + "\n"
                  + " Found:\n" + _existingBooks);
        }
        final HashMap<String, TableInfo.Column> _columnsPendingMutations = new HashMap<String, TableInfo.Column>(7);
        _columnsPendingMutations.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingMutations.put("bookUuid", new TableInfo.Column("bookUuid", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingMutations.put("operationType", new TableInfo.Column("operationType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingMutations.put("payloadJson", new TableInfo.Column("payloadJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingMutations.put("localImagePathsJson", new TableInfo.Column("localImagePathsJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingMutations.put("removedImagesJson", new TableInfo.Column("removedImagesJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingMutations.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPendingMutations = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPendingMutations = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPendingMutations = new TableInfo("pending_mutations", _columnsPendingMutations, _foreignKeysPendingMutations, _indicesPendingMutations);
        final TableInfo _existingPendingMutations = TableInfo.read(db, "pending_mutations");
        if (!_infoPendingMutations.equals(_existingPendingMutations)) {
          return new RoomOpenHelper.ValidationResult(false, "pending_mutations(com.homebooks.android.data.local.entity.PendingMutationEntity).\n"
                  + " Expected:\n" + _infoPendingMutations + "\n"
                  + " Found:\n" + _existingPendingMutations);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "9d7b5e47c78a47703d47390697901194", "f6e1b83f137316ec223db6fa125f23bb");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "books","pending_mutations");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `books`");
      _db.execSQL("DELETE FROM `pending_mutations`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(BookDao.class, BookDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(PendingMutationDao.class, PendingMutationDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public BookDao bookDao() {
    if (_bookDao != null) {
      return _bookDao;
    } else {
      synchronized(this) {
        if(_bookDao == null) {
          _bookDao = new BookDao_Impl(this);
        }
        return _bookDao;
      }
    }
  }

  @Override
  public PendingMutationDao pendingMutationDao() {
    if (_pendingMutationDao != null) {
      return _pendingMutationDao;
    } else {
      synchronized(this) {
        if(_pendingMutationDao == null) {
          _pendingMutationDao = new PendingMutationDao_Impl(this);
        }
        return _pendingMutationDao;
      }
    }
  }
}
