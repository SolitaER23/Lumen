package app.lumen.player

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lumen.player.data.HomeSort
import app.lumen.player.data.HomeSortStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 排序方式落盘的回归测试。
 *
 * 这类状态最容易出的问题是「读回来的值和写进去的不是一回事」：两个 tab 串了、
 * 脏数据没回退默认、写入越界值后菜单高亮和列表对不上。这里在真机/模拟器上用
 * 真实的 SharedPreferences 验一遍。
 */
@RunWith(AndroidJUnit4::class)
class HomeSortStoreTest {

    private val ctx: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun prefs() = ctx.getSharedPreferences(HomeSortStore.PREFS, Context.MODE_PRIVATE)

    @Before
    fun reset() {
        prefs().edit().clear().commit()
    }

    @Test
    fun defaultsToNameSortWhenNothingSaved() {
        assertEquals(HomeSort.DEFAULT, HomeSortStore.album(ctx))
        assertEquals(HomeSort.DEFAULT, HomeSortStore.artist(ctx))
        assertEquals(HomeSort.DEFAULT, HomeSortStore.sort(ctx, HomeSort.TAB_ALBUMS))
        assertEquals(HomeSort.DEFAULT, HomeSortStore.sort(ctx, HomeSort.TAB_ARTISTS))
    }

    @Test
    fun eachTabIsSavedIndependently() {
        HomeSortStore.setSort(ctx, HomeSort.TAB_ALBUMS, 2)
        HomeSortStore.setSort(ctx, HomeSort.TAB_ARTISTS, 1)

        assertEquals(2, HomeSortStore.album(ctx))
        assertEquals(1, HomeSortStore.artist(ctx))
        assertEquals(2, HomeSortStore.sort(ctx, HomeSort.TAB_ALBUMS))
        assertEquals(1, HomeSortStore.sort(ctx, HomeSort.TAB_ARTISTS))
    }

    @Test
    fun corruptSavedValueFallsBackToDefault() {
        // 模拟旧版本遗留 / 手改 prefs：越界值不能崩溃，也不能当成某一档
        prefs().edit().putInt("album", 9).putInt("artist", -3).commit()

        assertEquals(HomeSort.DEFAULT, HomeSortStore.album(ctx))
        assertEquals(HomeSort.DEFAULT, HomeSortStore.artist(ctx))
    }

    @Test
    fun invalidWriteIsRejectedNotClamped() {
        // 艺人页只有两档；写入 5 应回退默认并如实返回实际生效值
        assertEquals(HomeSort.DEFAULT, HomeSortStore.setSort(ctx, HomeSort.TAB_ARTISTS, 5))
        assertEquals(HomeSort.DEFAULT, HomeSortStore.artist(ctx))
    }
}
