package com.homebooks.android.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.homebooks.android.data.local.entity.PendingMutationEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class PendingMutationDao_Impl implements PendingMutationDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<PendingMutationEntity> __insertionAdapterOfPendingMutationEntity;

  private final EntityDeletionOrUpdateAdapter<PendingMutationEntity> __deletionAdapterOfPendingMutationEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteMutationById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteMutationsForBook;

  public PendingMutationDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPendingMutationEntity = new EntityInsertionAdapter<PendingMutationEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `pending_mutations` (`id`,`bookUuid`,`operationType`,`payloadJson`,`localImagePathsJson`,`removedImagesJson`,`timestamp`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PendingMutationEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getBookUuid());
        statement.bindString(3, entity.getOperationType());
        statement.bindString(4, entity.getPayloadJson());
        statement.bindString(5, entity.getLocalImagePathsJson());
        statement.bindString(6, entity.getRemovedImagesJson());
        statement.bindLong(7, entity.getTimestamp());
      }
    };
    this.__deletionAdapterOfPendingMutationEntity = new EntityDeletionOrUpdateAdapter<PendingMutationEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `pending_mutations` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PendingMutationEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteMutationById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM pending_mutations WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteMutationsForBook = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM pending_mutations WHERE bookUuid = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertMutation(final PendingMutationEntity mutation,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfPendingMutationEntity.insertAndReturnId(mutation);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteMutation(final PendingMutationEntity mutation,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfPendingMutationEntity.handle(mutation);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteMutationById(final long mutationId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteMutationById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, mutationId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteMutationById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteMutationsForBook(final String bookUuid,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteMutationsForBook.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, bookUuid);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteMutationsForBook.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllMutations(
      final Continuation<? super List<PendingMutationEntity>> $completion) {
    final String _sql = "SELECT * FROM pending_mutations ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PendingMutationEntity>>() {
      @Override
      @NonNull
      public List<PendingMutationEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBookUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "bookUuid");
          final int _cursorIndexOfOperationType = CursorUtil.getColumnIndexOrThrow(_cursor, "operationType");
          final int _cursorIndexOfPayloadJson = CursorUtil.getColumnIndexOrThrow(_cursor, "payloadJson");
          final int _cursorIndexOfLocalImagePathsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "localImagePathsJson");
          final int _cursorIndexOfRemovedImagesJson = CursorUtil.getColumnIndexOrThrow(_cursor, "removedImagesJson");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<PendingMutationEntity> _result = new ArrayList<PendingMutationEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PendingMutationEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpBookUuid;
            _tmpBookUuid = _cursor.getString(_cursorIndexOfBookUuid);
            final String _tmpOperationType;
            _tmpOperationType = _cursor.getString(_cursorIndexOfOperationType);
            final String _tmpPayloadJson;
            _tmpPayloadJson = _cursor.getString(_cursorIndexOfPayloadJson);
            final String _tmpLocalImagePathsJson;
            _tmpLocalImagePathsJson = _cursor.getString(_cursorIndexOfLocalImagePathsJson);
            final String _tmpRemovedImagesJson;
            _tmpRemovedImagesJson = _cursor.getString(_cursorIndexOfRemovedImagesJson);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new PendingMutationEntity(_tmpId,_tmpBookUuid,_tmpOperationType,_tmpPayloadJson,_tmpLocalImagePathsJson,_tmpRemovedImagesJson,_tmpTimestamp);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<PendingMutationEntity>> observeAllMutations() {
    final String _sql = "SELECT * FROM pending_mutations ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"pending_mutations"}, new Callable<List<PendingMutationEntity>>() {
      @Override
      @NonNull
      public List<PendingMutationEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBookUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "bookUuid");
          final int _cursorIndexOfOperationType = CursorUtil.getColumnIndexOrThrow(_cursor, "operationType");
          final int _cursorIndexOfPayloadJson = CursorUtil.getColumnIndexOrThrow(_cursor, "payloadJson");
          final int _cursorIndexOfLocalImagePathsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "localImagePathsJson");
          final int _cursorIndexOfRemovedImagesJson = CursorUtil.getColumnIndexOrThrow(_cursor, "removedImagesJson");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<PendingMutationEntity> _result = new ArrayList<PendingMutationEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PendingMutationEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpBookUuid;
            _tmpBookUuid = _cursor.getString(_cursorIndexOfBookUuid);
            final String _tmpOperationType;
            _tmpOperationType = _cursor.getString(_cursorIndexOfOperationType);
            final String _tmpPayloadJson;
            _tmpPayloadJson = _cursor.getString(_cursorIndexOfPayloadJson);
            final String _tmpLocalImagePathsJson;
            _tmpLocalImagePathsJson = _cursor.getString(_cursorIndexOfLocalImagePathsJson);
            final String _tmpRemovedImagesJson;
            _tmpRemovedImagesJson = _cursor.getString(_cursorIndexOfRemovedImagesJson);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new PendingMutationEntity(_tmpId,_tmpBookUuid,_tmpOperationType,_tmpPayloadJson,_tmpLocalImagePathsJson,_tmpRemovedImagesJson,_tmpTimestamp);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getMutationsForBook(final String bookUuid,
      final Continuation<? super List<PendingMutationEntity>> $completion) {
    final String _sql = "SELECT * FROM pending_mutations WHERE bookUuid = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, bookUuid);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PendingMutationEntity>>() {
      @Override
      @NonNull
      public List<PendingMutationEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBookUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "bookUuid");
          final int _cursorIndexOfOperationType = CursorUtil.getColumnIndexOrThrow(_cursor, "operationType");
          final int _cursorIndexOfPayloadJson = CursorUtil.getColumnIndexOrThrow(_cursor, "payloadJson");
          final int _cursorIndexOfLocalImagePathsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "localImagePathsJson");
          final int _cursorIndexOfRemovedImagesJson = CursorUtil.getColumnIndexOrThrow(_cursor, "removedImagesJson");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<PendingMutationEntity> _result = new ArrayList<PendingMutationEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PendingMutationEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpBookUuid;
            _tmpBookUuid = _cursor.getString(_cursorIndexOfBookUuid);
            final String _tmpOperationType;
            _tmpOperationType = _cursor.getString(_cursorIndexOfOperationType);
            final String _tmpPayloadJson;
            _tmpPayloadJson = _cursor.getString(_cursorIndexOfPayloadJson);
            final String _tmpLocalImagePathsJson;
            _tmpLocalImagePathsJson = _cursor.getString(_cursorIndexOfLocalImagePathsJson);
            final String _tmpRemovedImagesJson;
            _tmpRemovedImagesJson = _cursor.getString(_cursorIndexOfRemovedImagesJson);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new PendingMutationEntity(_tmpId,_tmpBookUuid,_tmpOperationType,_tmpPayloadJson,_tmpLocalImagePathsJson,_tmpRemovedImagesJson,_tmpTimestamp);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
