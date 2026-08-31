<template>
  <el-card class="main-card">
    <!-- 标题 -->
    <div class="title">{{ this.$route.name }}</div>
    <div class="operation-container">
      <el-button
        type="primary"
        size="small"
        icon="el-icon-plus"
        @click="openModel(null)"
      >
        新增模块
      </el-button>
      <!-- 数据筛选 -->
      <div style="margin-left:auto">
        <el-input
          v-model="keywords"
          prefix-icon="el-icon-search"
          size="small"
          placeholder="请输入资源名"
          style="width:200px"
          @keyup.enter="listResources"
        />
        <el-button
          type="primary"
          size="small"
          icon="el-icon-search"
          style="margin-left:1rem"
          @click="listResources"
        >
          搜索
        </el-button>
      </div>
    </div>
    <!-- 权限列表 -->
    <el-table
      v-loading="loading"
      :data="resourceList"
      row-key="id"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
    >
      <el-table-column prop="resourceName" label="资源名" width="220" />
      <el-table-column prop="url" label="资源路径" width="300" />
      <el-table-column prop="requetMethod" label="请求方式">
          <template #default="scope">
            <el-tag v-if="scope.row && scope.row.requestMethod" :type="tagType(scope.row.requestMethod)">
              {{ scope.row.requestMethod }}
            </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="isAnonymous" label="匿名访问" align="center">
        <template #default="scope">
          <el-switch
            v-if="scope.row.url"
            v-model="scope.row.isAnonymous"
            active-color="#13ce66"
            inactive-color="#F4F4F5"
            :active-value="1"
            :inactive-value="0"
            @change="changeResource(scope.row)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" align="center">
        <template #default="scope">
          <i class="el-icon-time" style="margin-right:5px" />
          {{ date(scope.row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="200">
        <template #default="scope">
          <el-button
            type="link"
            size="mini"
            @click="openAddResourceModel(scope.row)"
            v-if="scope.row.children"
          >
            <i class="el-icon-plus" /> 新增
          </el-button>
          <el-button
            type="link"
            size="mini"
            @click="openEditResourceModel(scope.row)"
          >
            <i class="el-icon-edit" /> 修改
          </el-button>
          <el-popconfirm
            title="确定删除吗？"
            style="margin-left:10px"
            @confirm="deleteResource(scope.row.id)"
          >
            <template #reference><el-button size="mini" type="link">
              <i class="el-icon-delete" /> 删除
            </el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <!-- 新增模态框 -->
    <el-dialog v-model="addModule" width="30%">
      <template #header><div class="dialog-title-container">{{ moduleTitle }}</div></template>
      <el-form label-width="80px" size="medium" :model="resourceForm">
        <el-form-item label="模块名">
          <el-input v-model="resourceForm.resourceName" style="width:220px" />
        </el-form-item>
      </el-form>
      <template #footer><span class="dialog-footer">
        <el-button @click="addModule = false">取 消</el-button>
        <el-button type="primary" @click="addOrEditResource">
          确 定
        </el-button>
      </span></template>
    </el-dialog>
    <!-- 新增模态框 -->
    <el-dialog v-model="addResource" width="30%">
      <template #header><div class="dialog-title-container">{{ resourceTitle }}</div></template>
      <el-form label-width="80px" size="medium" :model="resourceForm">
        <el-form-item label="资源名">
          <el-input v-model="resourceForm.resourceName" style="width:220px" />
        </el-form-item>
        <el-form-item label="资源路径">
          <el-input v-model="resourceForm.url" style="width:220px" />
        </el-form-item>
        <el-form-item label="请求方式">
          <el-radio-group v-model="resourceForm.requestMethod">
            <el-radio value="GET">GET</el-radio>
            <el-radio value="POST">POST</el-radio>
            <el-radio value="PUT">PUT</el-radio>
            <el-radio value="DELETE">DELETE</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer><span class="dialog-footer">
        <el-button @click="addResource = false">取 消</el-button>
        <el-button type="primary" @click="addOrEditResource">
          确 定
        </el-button>
      </span></template>
    </el-dialog>
  </el-card>
</template>

<script>
export default {
  created() {
    this.listResources();
  },
  data() {
    return {
      loading: true,
      keywords: "",
      resourceList: [],
      addModule: false,
      addResource: false,
      moduleTitle: "添加模块",
      resourceTitle: "添加资源",
      resourceForm: {}
    };
  },
  methods: {
    listResources() {
      this.$api.admin
        .resources({
          params: {
            keywords: this.keywords
          }
        })
        .then(data => {
          this.resourceList = data.data;
          this.loading = false;
        });
    },
    changeResource(resource) {
      this.$api.admin.saveResource(resource).then(data => {
        if (data.flag) {
          this.$notify.success({
            title: "成功",
            message: data.message
          });
          this.listResources();
        } else {
          this.$notify.error({
            title: "失败",
            message: data.message
          });
        }
      });
    },
    openModel(resource) {
      if (resource != null) {
        this.resourceForm = JSON.parse(JSON.stringify(resource));
        this.moduleTitle = "修改模块";
      } else {
        this.resourceForm = {};
        this.moduleTitle = "添加模块";
      }
      this.addModule = true;
    },
    openEditResourceModel(resource) {
      if (resource.url == null) {
        this.openModel(resource);
        return false;
      }
      this.resourceForm = JSON.parse(JSON.stringify(resource));
      this.resourceTitle = "修改资源";
      this.addResource = true;
    },
    openAddResourceModel(resource) {
      this.resourceForm = {};
      this.resourceForm.parentId = resource.id;
      this.resourceTitle = "添加资源";
      this.addResource = true;
    },
    deleteResource(id) {
      this.$api.admin.removeResource(id).then(data => {
        if (data.flag) {
          this.$notify.success({
            title: "成功",
            message: data.message
          });
          this.listResources();
        } else {
          this.$notify.error({
            title: "失败",
            message: data.message
          });
        }
      });
    },
    addOrEditResource() {
      if (String(this.resourceForm.resourceName || "").trim() == "") {
        this.$message.error("资源名不能为空");
        return false;
      }
      this.$api.admin
        .saveResource(this.resourceForm)
        .then(data => {
          if (data.flag) {
            this.$notify.success({
              title: "成功",
              message: data.message
            });
            this.listResources();
          } else {
            this.$notify.error({
              title: "失败",
              message: data.message
            });
          }
          this.addModule = false;
          this.addResource = false;
        });
    }
  },
  computed: {
    tagType() {
      return type => {
        switch (type) {
          case "GET":
            return "";
          case "POST":
            return "success";
          case "PUT":
            return "warning";
          case "DELETE":
            return "danger";
        }
      };
    }
  }
};
</script>
