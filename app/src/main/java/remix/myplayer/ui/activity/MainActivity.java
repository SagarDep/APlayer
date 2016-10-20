package remix.myplayer.ui.activity;


import android.Manifest;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.soundcloud.android.crop.Crop;
import com.umeng.analytics.MobclickAgent;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import remix.myplayer.R;
import remix.myplayer.adapter.PagerAdapter;
import remix.myplayer.fragment.AlbumFragment;
import remix.myplayer.fragment.ArtistFragment;
import remix.myplayer.fragment.BottomActionBarFragment;
import remix.myplayer.fragment.PlayListFragment;
import remix.myplayer.fragment.SongFragment;
import remix.myplayer.interfaces.OnUpdateOptionMenuListener;
import remix.myplayer.model.MP3Item;
import remix.myplayer.service.MusicService;
import remix.myplayer.theme.ThemeStore;
import remix.myplayer.ui.MultiChoice;
import remix.myplayer.util.ColorUtil;
import remix.myplayer.util.CommonUtil;
import remix.myplayer.util.Constants;
import remix.myplayer.util.MediaStoreUtil;
import remix.myplayer.util.DiskCache;
import remix.myplayer.util.Global;
import remix.myplayer.util.LogUtil;
import remix.myplayer.util.PlayListUtil;
import remix.myplayer.util.SPUtil;
import remix.myplayer.util.StatusBarUtil;
import remix.myplayer.util.ToastUtil;

/**
 *
 */
public class MainActivity extends MultiChoiceActivity implements MusicService.Callback {
    public static MainActivity mInstance = null;
    @BindView(R.id.toolbar)
    Toolbar mToolBar;
    @BindView(R.id.tabs)
    TabLayout mTablayout;
    @BindView(R.id.ViewPager)
    android.support.v4.view.ViewPager mViewPager;
    @BindView(R.id.navigation_view)
    NavigationView mNavigationView;
    @BindView(R.id.drawer_layout)
    DrawerLayout mDrawerLayout;
    @BindView(R.id.add)
    FloatingActionButton mAddButton;
    private BottomActionBarFragment mBottomBar;
    private final static String TAG = "MainActivity";

    private PagerAdapter mAdapter;
    //是否正在运行
    private static boolean mIsRunning = false;
    //是否第一次启动
    private boolean mIsFirst = true;
    //是否是安装后第一次打开软件
    private boolean mIsFirstAfterInstall = true;
    //是否是第一次创建activity
    private static boolean mIsFirstCreateActivity = true;
    private static final int PERMISSIONCODE = 100;
    private static final String[] PERMISSIONS = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE};

    //更新主题
    private final int UPDATE_THEME = 1;
    private Handler mRefreshHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            if(msg.what == Constants.RECREATE_ACTIVITY) {
                recreate();
            }
            else if(msg.what == Constants.CLEAR_MULTI){
                mMultiChoice.clearSelectedViews();
            }
            else if (msg.what == Constants.UPDATE_ADAPTER || msg.what == Constants.UPDATE_ALLSONG_ADAPTER){
                boolean isAllSongOnly = msg.what == Constants.UPDATE_ADAPTER;
                //刷新适配器
                for(Fragment temp : getSupportFragmentManager().getFragments()){
                    if(temp instanceof SongFragment){
                        SongFragment songFragment = (SongFragment)temp;
                        if(songFragment.getAdapter() != null){
                            songFragment.getAdapter().notifyDataSetChanged();
                        }
                        if(isAllSongOnly)
                            return;
                    }
                    if(temp instanceof AlbumFragment){
                        AlbumFragment albumFragment = (AlbumFragment)temp;
                        if(albumFragment.getAdapter() != null){
                            albumFragment.getAdapter().notifyDataSetChanged();
                        }
                    }
                    if(temp instanceof ArtistFragment){
                        ArtistFragment artistFragment = (ArtistFragment)temp;
                        if(artistFragment.getAdapter() != null){
                            artistFragment.getAdapter().notifyDataSetChanged();
                        }
                    }
                    if(temp instanceof PlayListFragment){
                        PlayListFragment playListFragment = (PlayListFragment) temp;
                        if(playListFragment.getAdapter() != null){
                            playListFragment.getAdapter().notifyDataSetChanged();
                        }
                    }
                }
            }
        }
    };


    @Override
    protected void onResume() {
        MobclickAgent.onPageStart(MainActivity.class.getSimpleName());
        super.onResume();
        if(mMultiChoice.isShow()){
            mRefreshHandler.sendEmptyMessage(Constants.UPDATE_ADAPTER);
        }
        mIsRunning = true;
        //更新UI
        UpdateUI(MusicService.getCurrentMP3(), MusicService.getIsplay());
    }

    @Override
    protected void onPause() {
        MobclickAgent.onPageStart(MainActivity.class.getSimpleName());
        super.onPause();
        if(mMultiChoice.isShow()){
            mRefreshHandler.sendEmptyMessageDelayed(Constants.CLEAR_MULTI,500);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mIsRunning = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        initTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);
        mInstance = this;

        mMultiChoice.setOnUpdateOptionMenuListener(new OnUpdateOptionMenuListener() {
            @Override
            public void onUpdate(boolean multiShow) {
                mMultiChoice.setShowing(multiShow);
                mToolBar.setNavigationIcon(mMultiChoice.isShow() ? R.drawable.actionbar_delete : R.drawable.actionbar_menu);
                mToolBar.setNavigationOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(mMultiChoice.isShow()){
                            mMultiChoice.UpdateOptionMenu(false);
                            mMultiChoice.clear();
                        } else {
                            mDrawerLayout.openDrawer(mNavigationView);
                        }
                    }
                });
                if(!mMultiChoice.isShow()){
                    mMultiChoice.clear();
                }
                invalidateOptionsMenu();
            }
        });

        //播放的service
        MusicService.addCallback(MainActivity.this);

        //初始化toolbar
        initToolbar(mToolBar,"");
        initPager();
        initTab();
        //初始化测滑菜单
        initDrawerLayout();
        //根据主题设置颜色
        initColor();
        //初始化底部状态栏
        mBottomBar = (BottomActionBarFragment) getSupportFragmentManager().findFragmentById(R.id.bottom_actionbar_new);

        mIsFirstAfterInstall = SPUtil.getValue(this, "Setting", "First", true);
        SPUtil.putValue(this, "Setting", "First", false);

        if(mIsFirstCreateActivity)
            initLastSong();

//        BmobUpdateAgent.setUpdateOnlyWifi(false);
//        BmobUpdateAgent.update(this);

    }

    /**
     * 初始化上一次退出时播放的歌曲
     * 默认为第一首歌曲
     */
    private void initLastSong() {
        //第一次打开软件，播放队列以及正在播放歌曲都为空
//        if(mIsFirstAfterInstall){
//            mBottomBar.UpdateBottomStatus(new MP3Item(),false);
//        }
        mIsFirstCreateActivity = false;

        if(Global.mPlayQueue == null || Global.mPlayQueue.size() == 0)
            return;
        //如果是第一次打开，设置第一首歌曲为正在播放
        if(mIsFirstAfterInstall){
            int id =  Global.mPlayQueue.get(0);
            MP3Item item = MediaStoreUtil.getMP3InfoById(id);
            if(item != null){
                mBottomBar.UpdateBottomStatus(item,false);
                SPUtil.putValue(this,"Setting","LastSongId",id);
                MusicService.initDataSource(item,0);
            }
            return;
        }

        //读取上次退出时正在播放的歌曲的id
        int lastId = SPUtil.getValue(this,"Setting","LastSongId",0);
        //上次退出时正在播放的歌曲是否还存在
        boolean isLastSongExist = false;
        //上次退出时正在播放的歌曲的pos
        int pos = 0;
        //查找上次退出时的歌曲是否还存在

        for(int i = 0 ; i < Global.mAllSongList.size();i++){
            if(lastId == Global.mAllSongList.get(i)){
                isLastSongExist = true;
                pos = i;
                break;
            }
        }

        boolean isPlay = !mIsFirst && MusicService.getIsplay();
        if(mIsFirst){
            mIsFirst = false;
            MP3Item item = null;
            //上次退出时保存的正在播放的歌曲未失效
            if(isLastSongExist && (item = MediaStoreUtil.getMP3InfoById(lastId)) != null) {
                mBottomBar.UpdateBottomStatus(item, isPlay);
                MusicService.initDataSource(item,pos);
            }else {
                if(Global.mPlayQueue.size() > 0){
                    //重新找到一个歌曲id
                    int id =  Global.mPlayQueue.get(0);
                    for(int i = 0; i < Global.mPlayQueue.size() ; i++){
                        id = Global.mPlayQueue.get(i);
                        if (id != lastId)
                            break;
                    }
                    item = MediaStoreUtil.getMP3InfoById(id);
                    mBottomBar.UpdateBottomStatus(item,isPlay);
                    SPUtil.putValue(this,"Setting","LastSongId",id);
                    MusicService.initDataSource(item,0);
                }
            }
        } else {
            mBottomBar.UpdateBottomStatus(MusicService.getCurrentMP3(), MusicService.getIsplay());
        }
    }

    /**
     * 初始化主题
     */
    private void initTheme() {
        ThemeStore.THEME_MODE = ThemeStore.loadThemeMode();
        ThemeStore.THEME_COLOR = ThemeStore.loadThemeColor();

        ThemeStore.MATERIAL_COLOR_PRIMARY = ThemeStore.getMaterialPrimaryColorRes();
        ThemeStore.MATERIAL_COLOR_PRIMARY_DARK = ThemeStore.getMaterialPrimaryDarkColorRes();
        LogUtil.d(TAG,"primary:" + ThemeStore.MATERIAL_COLOR_PRIMARY + "\r\nprimary dark:" + ThemeStore.MATERIAL_COLOR_PRIMARY_DARK);
//        int color = ThemeStore.getMaterialPrimaryColorRes(this);
//        ThemeStore.MATERIAL_COLOR_PRIMARY_DARK = ThemeStore.getMaterialPrimaryColorRes(ThemeStore.THEME_COLOR);
    }

    private void initColor() {
        mToolBar.setBackgroundColor(ColorUtil.getColor(ThemeStore.MATERIAL_COLOR_PRIMARY));
        mTablayout.setBackgroundColor(ColorUtil.getColor(ThemeStore.MATERIAL_COLOR_PRIMARY));
    }


    @Override
    protected void setStatusBar() {
        StatusBarUtil.setColorNoTranslucentForDrawerLayout(this,
                (DrawerLayout) findViewById(R.id.drawer_layout),
                ColorUtil.getColor(ThemeStore.MATERIAL_COLOR_PRIMARY_DARK));
    }

    @Override
    protected void initToolbar(Toolbar toolbar, String title) {
        super.initToolbar(toolbar,"");
        mToolBar.setTitle("");

        mToolBar.setNavigationIcon(R.drawable.actionbar_menu);
        mToolBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDrawerLayout.openDrawer(mNavigationView);
            }
        });
    }


    public PagerAdapter getAdapter() {
        return mAdapter;
    }

    @OnClick(R.id.add)
    public void onClick(View v){
        switch (v.getId()){
            case R.id.add:
                if(mMultiChoice.isShow())
                    return;
                new MaterialDialog.Builder(MainActivity.this)
                        .title("新建播放列表")
                        .titleColor(ThemeStore.getTextColorPrimary())
                        .positiveText("创建")
                        .positiveColor(ThemeStore.getMaterialColorPrimaryColor())
                        .negativeText("取消")
                        .negativeColor(ThemeStore.getMaterialColorPrimaryColor())
                        .backgroundColor(ThemeStore.getBackgroundColor3())
                        .content(R.string.input_playlist_name)
                        .contentColor(ThemeStore.getTextColorPrimary())
                        .inputRange(1,15)
                        .input("", "本地歌单" + Global.mPlayList.size(), new MaterialDialog.InputCallback() {
                            @Override
                            public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                                if(!TextUtils.isEmpty(input)){
                                    int newPlayListId = PlayListUtil.addPlayList(input.toString());
                                    ToastUtil.show(MainActivity.this, newPlayListId > 0 ?
                                                    R.string.add_playlist_success :
                                                    newPlayListId == -1 ? R.string.add_playlist_error : R.string.playlist_alread_exist,
                                            Toast.LENGTH_SHORT);
                                }
                            }
                        })
                        .dismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                if(mAdapter != null)
                                    mAdapter.notifyDataSetChanged();
                            }
                        })
                        .show();
                break;
        }
    }

    //初始化ViewPager
    private void initPager() {
        mAdapter = new PagerAdapter(getSupportFragmentManager());
        mAdapter.setTitles(new String[]{getResources().getString(R.string.tab_song),
                getResources().getString(R.string.tab_album),
                getResources().getString(R.string.tab_artist),
                getResources().getString(R.string.tab_playlist)});
        mAdapter.AddFragment(new SongFragment());
        mAdapter.AddFragment(new AlbumFragment());
        mAdapter.AddFragment(new ArtistFragment());
        mAdapter.AddFragment(new PlayListFragment());

        mViewPager.setAdapter(mAdapter);
        mViewPager.setCurrentItem(0);
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                mAddButton.setVisibility(position == 3 ? View.VISIBLE: View.GONE);
            }
            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
    }

    //初始化custontab
    private void initTab() {
        //添加tab选项卡
        mTablayout.addTab(mTablayout.newTab().setText(getResources().getString(R.string.tab_song)));
        mTablayout.addTab(mTablayout.newTab().setText(getResources().getString(R.string.tab_album)));
        mTablayout.addTab(mTablayout.newTab().setText(getResources().getString(R.string.tab_artist)));
        mTablayout.addTab(mTablayout.newTab().setText(getResources().getString(R.string.tab_playlist)));
        //viewpager与tablayout关联
        mTablayout.setupWithViewPager(mViewPager);
    }

    private void initDrawerLayout() {
        mNavigationView.setItemTextAppearance(R.style.Drawer_text_style);
        ColorStateList colorStateList = new ColorStateList(new int[][]{{android.R.attr.state_pressed},{android.R.attr.state_checked} ,{}},
                new int[]{ColorUtil.getColor(ThemeStore.MATERIAL_COLOR_PRIMARY), ColorUtil.getColor(ThemeStore.MATERIAL_COLOR_PRIMARY),ColorUtil.getColor(R.color.black_737373)});
        mNavigationView.setItemIconTintList(colorStateList);
        mNavigationView.setItemTextColor(colorStateList);
        mNavigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                item.setChecked(true);
                switch (item.getItemId()) {
                    case R.id.item_recently:
                        //最近添加
                        startActivity(new Intent(MainActivity.this, RecetenlyActivity.class));
                        break;
                    case R.id.item_folder:
                        startActivity(new Intent(MainActivity.this, FolderActivity.class));
                        break;
                    case R.id.item_allsong:
                        mDrawerLayout.closeDrawer(mNavigationView);
                        break;
                    case R.id.item_setting:
                        //设置
                        startActivityForResult(new Intent(MainActivity.this,SettingActivity.class),UPDATE_THEME);
//                        startActivityForResult(new Intent(MainActivity.this,ThemeActivity.class),UPDATE_THEME);
                        break;
                    case R.id.item_exit:
                        sendBroadcast(new Intent(Constants.EXIT));
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(data != null){
            boolean isAlbum = Global.mAlbunOrArtist == Constants.ALBUM;
            String errorTxt = isAlbum ? "设置专辑封面失败" : "设置艺术家封面失败";
            int id = Global.mAlbumArtistID; //专辑或艺术家封面
            String name = Global.mAlbumArtistName;
            switch (requestCode){
                //重启activity
                case UPDATE_THEME:
                    if(data.getBooleanExtra("needRefresh",false))
                        mRefreshHandler.sendEmptyMessage(Constants.RECREATE_ACTIVITY);
                    break;
                //图片选择
                case Crop.REQUEST_PICK:
                    if(resultCode == RESULT_OK){
                        File cacheDir = DiskCache.getDiskCacheDir(this,"thumbnail/" + (isAlbum ? "album" : "artist"));
                        if(!cacheDir.exists()){
                            if(!cacheDir.mkdir()){
                                Toast.makeText(this,errorTxt,Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                        Uri destination = Uri.fromFile(new File(cacheDir, CommonUtil.hashKeyForDisk((id * 255 ) + "")));
                        Crop.of(data.getData(), destination).asSquare().start(this);
                    } else {
                        Toast.makeText(this,errorTxt,Toast.LENGTH_SHORT).show();
                    }
                    break;
                //图片裁剪
                case Crop.REQUEST_CROP:
                    //裁剪后的图片路径
                    String path = Crop.getOutput(data).getEncodedPath();
                    if(TextUtils.isEmpty(path) || id == -1){
                        Toast.makeText(MainActivity.this, errorTxt, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    //清除fresco的缓存
                    ImagePipeline imagePipeline = Fresco.getImagePipeline();
                    Uri fileUri = Uri.parse("file:///" + path);
                    Uri providerUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), id);
                    imagePipeline.evictFromCache(fileUri);
                    imagePipeline.evictFromCache(providerUri);
                    mRefreshHandler.sendEmptyMessage(Constants.UPDATE_ADAPTER);
                    break;
            }
        }
    }


    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(mNavigationView)) {
            mDrawerLayout.closeDrawer(mNavigationView);
        } else if(mMultiChoice.isShow()) {
//            updateOptionsMenu(false);
            mMultiChoice.UpdateOptionMenu(false);
        } else {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            home.addCategory(Intent.CATEGORY_HOME);
            startActivity(home);
            Intent intent = new Intent(Constants.NOTIFY);
            intent.putExtra("FromMainActivity", true);
            sendBroadcast(intent);
        }
    }

    //更新界面
    @Override
    public void UpdateUI(MP3Item mp3Item, boolean isplay) {
        if (!mIsRunning)
            return;
        mBottomBar.UpdateBottomStatus(mp3Item, isplay);
//        List<Fragment> fragmentList = getSupportFragmentManager().getFragments();
//        for (Fragment fragment : fragmentList) {
//            if (fragment instanceof SongFragment && ((SongFragment) fragment).getAdapter() != null) {
//                ((SongFragment) fragment).getAdapter().notifyDataSetChanged();
//            }
//        }
        mRefreshHandler.sendEmptyMessage(Constants.UPDATE_ALLSONG_ADAPTER);
        View headView = mNavigationView.getHeaderView(0);
        if(headView != null && mp3Item != null){
            TextView textView = (TextView) headView.findViewById(R.id.header_txt);
            textView.setText(mp3Item.getArtist() + "-" + mp3Item.getTitle());
        }
    }

    @Override
    public int getType() {
        return Constants.MAINACTIVITY;
    }

}

