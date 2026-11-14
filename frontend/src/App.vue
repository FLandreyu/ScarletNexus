<template>
  <div class="app-shell">
    <a class="skip-link" href="#main">跳到主要内容</a>
    <header class="site-header">
      <RouterLink to="/" class="brand" aria-label="Scarlet Nexus 首页">
        <span class="brand-mark" aria-hidden="true">S<span>N</span></span>
        <span
          >SCARLET <strong>NEXUS</strong><small>个人终身学习系统</small></span
        >
      </RouterLink>
      <span class="header-label"
        >PRIVATE WORKSPACE <span aria-hidden="true">/</span> P0</span
      >
    </header>
    <main id="main" tabindex="-1"><RouterView /></main>
    <footer class="site-footer">
      Scarlet Nexus <span>让每一步学习都有迹可循。</span>
    </footer>
  </div>
</template>
