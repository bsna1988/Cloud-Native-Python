package com.homebooks.android.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.homebooks.android.data.local.entity.BookEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
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
public final class BookDao_Impl implements BookDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<BookEntity> __insertionAdapterOfBookEntity;

  private final EntityDeletionOrUpdateAdapter<BookEntity> __updateAdapterOfBookEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteBookByUuid;

  public BookDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfBookEntity = new EntityInsertionAdapter<BookEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `books` (`uuid`,`serverId`,`title`,`author`,`barcode`,`releaseDate`,`description`,`thumbnail`,`imagesJson`,`syncState`,`isDeleted`,`updatedAt`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BookEntity entity) {
        statement.bindString(1, entity.getUuid());
        if (entity.getServerId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindLong(2, entity.getServerId());
        }
        statement.bindString(3, entity.getTitle());
        statement.bindString(4, entity.getAuthor());
        if (entity.getBarcode() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getBarcode());
        }
        statement.bindString(6, entity.getReleaseDate());
        statement.bindString(7, entity.getDescription());
        if (entity.getThumbnail() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getThumbnail());
        }
        statement.bindString(9, entity.getImagesJson());
        statement.bindString(10, entity.getSyncState());
        final int _tmp = entity.isDeleted() ? 1 : 0;
        statement.bindLong(11, _tmp);
        statement.bindLong(12, entity.getUpdatedAt());
      }
    };
    this.__updateAdapterOfBookEntity = new EntityDeletionOrUpdateAdapter<BookEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `books` SET `uuid` = ?,`serverId` = ?,`title` = ?,`author` = ?,`barcode` = ?,`releaseDate` = ?,`description` = ?,`thumbnail` = ?,`imagesJson` = ?,`syncState` = ?,`isDeleted` = ?,`updatedAt` = ? WHERE `uuid` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BookEntity entity) {
        statement.bindString(1, entity.getUuid());
        if (entity.getServerId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindLong(2, entity.getServerId());
        }
        statement.bindString(3, entity.getTitle());
        statement.bindString(4, entity.getAuthor());
        if (entity.getBarcode() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getBarcode());
        }
        statement.bindString(6, entity.getReleaseDate());
        statement.bindString(7, entity.getDescription());
        if (entity.getThumbnail() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getThumbnail());
        }
        statement.bindString(9, entity.getImagesJson());
        statement.bindString(10, entity.getSyncState());
        final int _tmp = entity.isDeleted() ? 1 : 0;
        statement.bindLong(11, _tmp);
        statement.bindLong(12, entity.getUpdatedAt());
        statement.bindString(13, entity.getUuid());
      }
    };
    this.__preparedStmtOfDeleteBookByUuid = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM books WHERE uuid = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertBook(final BookEntity book, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfBookEntity.insert(book);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateBook(final BookEntity book, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfBookEntity.handle(book);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteBookByUuid(final String uuid, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteBookByUuid.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, uuid);
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
          __preparedStmtOfDeleteBookByUuid.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<BookEntity>> observeBooks() {
    final String _sql = "SELECT * FROM books WHERE isDeleted = 0 ORDER BY title ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"books"}, new Callable<List<BookEntity>>() {
      @Override
      @NonNull
      public List<BookEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfServerId = CursorUtil.getColumnIndexOrThrow(_cursor, "serverId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfAuthor = CursorUtil.getColumnIndexOrThrow(_cursor, "author");
          final int _cursorIndexOfBarcode = CursorUtil.getColumnIndexOrThrow(_cursor, "barcode");
          final int _cursorIndexOfReleaseDate = CursorUtil.getColumnIndexOrThrow(_cursor, "releaseDate");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfThumbnail = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnail");
          final int _cursorIndexOfImagesJson = CursorUtil.getColumnIndexOrThrow(_cursor, "imagesJson");
          final int _cursorIndexOfSyncState = CursorUtil.getColumnIndexOrThrow(_cursor, "syncState");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final List<BookEntity> _result = new ArrayList<BookEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BookEntity _item;
            final String _tmpUuid;
            _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            final Integer _tmpServerId;
            if (_cursor.isNull(_cursorIndexOfServerId)) {
              _tmpServerId = null;
            } else {
              _tmpServerId = _cursor.getInt(_cursorIndexOfServerId);
            }
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpAuthor;
            _tmpAuthor = _cursor.getString(_cursorIndexOfAuthor);
            final String _tmpBarcode;
            if (_cursor.isNull(_cursorIndexOfBarcode)) {
              _tmpBarcode = null;
            } else {
              _tmpBarcode = _cursor.getString(_cursorIndexOfBarcode);
            }
            final String _tmpReleaseDate;
            _tmpReleaseDate = _cursor.getString(_cursorIndexOfReleaseDate);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final String _tmpThumbnail;
            if (_cursor.isNull(_cursorIndexOfThumbnail)) {
              _tmpThumbnail = null;
            } else {
              _tmpThumbnail = _cursor.getString(_cursorIndexOfThumbnail);
            }
            final String _tmpImagesJson;
            _tmpImagesJson = _cursor.getString(_cursorIndexOfImagesJson);
            final String _tmpSyncState;
            _tmpSyncState = _cursor.getString(_cursorIndexOfSyncState);
            final boolean _tmpIsDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp != 0;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new BookEntity(_tmpUuid,_tmpServerId,_tmpTitle,_tmpAuthor,_tmpBarcode,_tmpReleaseDate,_tmpDescription,_tmpThumbnail,_tmpImagesJson,_tmpSyncState,_tmpIsDeleted,_tmpUpdatedAt);
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
  public Object getBooksList(final Continuation<? super List<BookEntity>> $completion) {
    final String _sql = "SELECT * FROM books WHERE isDeleted = 0 ORDER BY title ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<BookEntity>>() {
      @Override
      @NonNull
      public List<BookEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfServerId = CursorUtil.getColumnIndexOrThrow(_cursor, "serverId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfAuthor = CursorUtil.getColumnIndexOrThrow(_cursor, "author");
          final int _cursorIndexOfBarcode = CursorUtil.getColumnIndexOrThrow(_cursor, "barcode");
          final int _cursorIndexOfReleaseDate = CursorUtil.getColumnIndexOrThrow(_cursor, "releaseDate");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfThumbnail = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnail");
          final int _cursorIndexOfImagesJson = CursorUtil.getColumnIndexOrThrow(_cursor, "imagesJson");
          final int _cursorIndexOfSyncState = CursorUtil.getColumnIndexOrThrow(_cursor, "syncState");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final List<BookEntity> _result = new ArrayList<BookEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BookEntity _item;
            final String _tmpUuid;
            _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            final Integer _tmpServerId;
            if (_cursor.isNull(_cursorIndexOfServerId)) {
              _tmpServerId = null;
            } else {
              _tmpServerId = _cursor.getInt(_cursorIndexOfServerId);
            }
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpAuthor;
            _tmpAuthor = _cursor.getString(_cursorIndexOfAuthor);
            final String _tmpBarcode;
            if (_cursor.isNull(_cursorIndexOfBarcode)) {
              _tmpBarcode = null;
            } else {
              _tmpBarcode = _cursor.getString(_cursorIndexOfBarcode);
            }
            final String _tmpReleaseDate;
            _tmpReleaseDate = _cursor.getString(_cursorIndexOfReleaseDate);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final String _tmpThumbnail;
            if (_cursor.isNull(_cursorIndexOfThumbnail)) {
              _tmpThumbnail = null;
            } else {
              _tmpThumbnail = _cursor.getString(_cursorIndexOfThumbnail);
            }
            final String _tmpImagesJson;
            _tmpImagesJson = _cursor.getString(_cursorIndexOfImagesJson);
            final String _tmpSyncState;
            _tmpSyncState = _cursor.getString(_cursorIndexOfSyncState);
            final boolean _tmpIsDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp != 0;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new BookEntity(_tmpUuid,_tmpServerId,_tmpTitle,_tmpAuthor,_tmpBarcode,_tmpReleaseDate,_tmpDescription,_tmpThumbnail,_tmpImagesJson,_tmpSyncState,_tmpIsDeleted,_tmpUpdatedAt);
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
  public Object getBookByUuid(final String uuid,
      final Continuation<? super BookEntity> $completion) {
    final String _sql = "SELECT * FROM books WHERE uuid = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, uuid);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<BookEntity>() {
      @Override
      @Nullable
      public BookEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfServerId = CursorUtil.getColumnIndexOrThrow(_cursor, "serverId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfAuthor = CursorUtil.getColumnIndexOrThrow(_cursor, "author");
          final int _cursorIndexOfBarcode = CursorUtil.getColumnIndexOrThrow(_cursor, "barcode");
          final int _cursorIndexOfReleaseDate = CursorUtil.getColumnIndexOrThrow(_cursor, "releaseDate");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfThumbnail = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnail");
          final int _cursorIndexOfImagesJson = CursorUtil.getColumnIndexOrThrow(_cursor, "imagesJson");
          final int _cursorIndexOfSyncState = CursorUtil.getColumnIndexOrThrow(_cursor, "syncState");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final BookEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpUuid;
            _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            final Integer _tmpServerId;
            if (_cursor.isNull(_cursorIndexOfServerId)) {
              _tmpServerId = null;
            } else {
              _tmpServerId = _cursor.getInt(_cursorIndexOfServerId);
            }
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpAuthor;
            _tmpAuthor = _cursor.getString(_cursorIndexOfAuthor);
            final String _tmpBarcode;
            if (_cursor.isNull(_cursorIndexOfBarcode)) {
              _tmpBarcode = null;
            } else {
              _tmpBarcode = _cursor.getString(_cursorIndexOfBarcode);
            }
            final String _tmpReleaseDate;
            _tmpReleaseDate = _cursor.getString(_cursorIndexOfReleaseDate);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final String _tmpThumbnail;
            if (_cursor.isNull(_cursorIndexOfThumbnail)) {
              _tmpThumbnail = null;
            } else {
              _tmpThumbnail = _cursor.getString(_cursorIndexOfThumbnail);
            }
            final String _tmpImagesJson;
            _tmpImagesJson = _cursor.getString(_cursorIndexOfImagesJson);
            final String _tmpSyncState;
            _tmpSyncState = _cursor.getString(_cursorIndexOfSyncState);
            final boolean _tmpIsDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp != 0;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _result = new BookEntity(_tmpUuid,_tmpServerId,_tmpTitle,_tmpAuthor,_tmpBarcode,_tmpReleaseDate,_tmpDescription,_tmpThumbnail,_tmpImagesJson,_tmpSyncState,_tmpIsDeleted,_tmpUpdatedAt);
          } else {
            _result = null;
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
  public Object getBookByServerId(final int serverId,
      final Continuation<? super BookEntity> $completion) {
    final String _sql = "SELECT * FROM books WHERE serverId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, serverId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<BookEntity>() {
      @Override
      @Nullable
      public BookEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfUuid = CursorUtil.getColumnIndexOrThrow(_cursor, "uuid");
          final int _cursorIndexOfServerId = CursorUtil.getColumnIndexOrThrow(_cursor, "serverId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfAuthor = CursorUtil.getColumnIndexOrThrow(_cursor, "author");
          final int _cursorIndexOfBarcode = CursorUtil.getColumnIndexOrThrow(_cursor, "barcode");
          final int _cursorIndexOfReleaseDate = CursorUtil.getColumnIndexOrThrow(_cursor, "releaseDate");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfThumbnail = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnail");
          final int _cursorIndexOfImagesJson = CursorUtil.getColumnIndexOrThrow(_cursor, "imagesJson");
          final int _cursorIndexOfSyncState = CursorUtil.getColumnIndexOrThrow(_cursor, "syncState");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final BookEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpUuid;
            _tmpUuid = _cursor.getString(_cursorIndexOfUuid);
            final Integer _tmpServerId;
            if (_cursor.isNull(_cursorIndexOfServerId)) {
              _tmpServerId = null;
            } else {
              _tmpServerId = _cursor.getInt(_cursorIndexOfServerId);
            }
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpAuthor;
            _tmpAuthor = _cursor.getString(_cursorIndexOfAuthor);
            final String _tmpBarcode;
            if (_cursor.isNull(_cursorIndexOfBarcode)) {
              _tmpBarcode = null;
            } else {
              _tmpBarcode = _cursor.getString(_cursorIndexOfBarcode);
            }
            final String _tmpReleaseDate;
            _tmpReleaseDate = _cursor.getString(_cursorIndexOfReleaseDate);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final String _tmpThumbnail;
            if (_cursor.isNull(_cursorIndexOfThumbnail)) {
              _tmpThumbnail = null;
            } else {
              _tmpThumbnail = _cursor.getString(_cursorIndexOfThumbnail);
            }
            final String _tmpImagesJson;
            _tmpImagesJson = _cursor.getString(_cursorIndexOfImagesJson);
            final String _tmpSyncState;
            _tmpSyncState = _cursor.getString(_cursorIndexOfSyncState);
            final boolean _tmpIsDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp != 0;
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _result = new BookEntity(_tmpUuid,_tmpServerId,_tmpTitle,_tmpAuthor,_tmpBarcode,_tmpReleaseDate,_tmpDescription,_tmpThumbnail,_tmpImagesJson,_tmpSyncState,_tmpIsDeleted,_tmpUpdatedAt);
          } else {
            _result = null;
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
