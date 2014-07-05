package models.conf

import enums.LevelEnum
import enums.LevelEnum.Level
import play.api.Logger
import play.api.Play.current
import models.PlayCache
import org.joda.time.DateTime
import utils.TaskTools

import scala.slick.driver.MySQLDriver.simple._
import com.github.tototoshi.slick.MySQLJodaSupport._

/**
 * 子项目
 *
 * @author of546
 */
case class Version(id: Option[Int], pid: Int, vs: String, updated: DateTime)
class VersionTable(tag: Tag) extends Table[Version](tag, "version"){
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def pid = column[Int]("pid", O.NotNull)   // 项目编号
  def vs = column[String]("version", O.NotNull) // 当前版本
  def updated= column[DateTime]("updated", O.Default(DateTime.now()))

  override def * = (id.?, pid, vs, updated) <> (Version.tupled, Version.unapply _)
  def idx = index("idx_pid", pid)
  def idx_vs = index("idx_pid_vs", (pid, vs), unique = true)
}
object VersionHelper extends PlayCache {

  import models.AppDB._

  val qVersion = TableQuery[VersionTable]

  def findById(id: Int) = db withSession { implicit session =>
    qVersion.where(_.id is id).firstOption
  }

  def findByPid(pid: Int): List[Version] = db withSession { implicit session =>
    qVersion.where(_.pid is pid).sortBy(_.updated.desc).list
  }

  def count(pid: Int) = db withSession { implicit session =>
    Query(qVersion.where(_.pid is pid).length).first
  }

  def all(pid: Int, page: Int, pageSize: Int): List[Version] = db withSession { implicit session =>
    val offset = pageSize * page
    qVersion.where(_.pid is pid).drop(offset).take(pageSize).list
  }

  def all(pid: Int, top: Int): List[Version] = db withSession { implicit session =>
    qVersion.where(_.pid is pid).sortBy(_.id desc).take(top).list
  }

  def findByPidAndEid(pid: Int, eid: Int): List[Version] = db withSession {implicit session =>
    //1、获取环境的level
    val level: Level = EnvironmentHelper.findById(eid).get.level
    Logger.info(level.toString)
    val list = findByPid(pid)
    if(level == LevelEnum.unsafe){//开发&测试
      Logger.info("unsafe")
      list
    } else {//线上环境
      Logger.info("safe")
      list.filterNot(t => TaskTools.isSnapshot(t.vs))
    }
  }

  def create(version: Version) = db withTransaction { implicit session =>
    val vid = qVersion.returning(qVersion.map(_.id)).insert(version)
    ProjectHelper.findById(version.pid) match {
        case Some(p) =>
          ProjectHelper.update_(version.pid, Project(p.id, p.name, p.templateId, p.subTotal + 1, Some(vid), Some(version.vs), Some(version.updated)))
        case None =>
    }
    vid
  }

  def delete(version: Version): Int = db withTransaction { implicit session =>
    ProjectHelper.findById(version.pid) match {
      case Some(p) =>
        ProjectHelper.update_(version.pid, Project(p.id, p.name, p.templateId, p.subTotal - 1, p.lastVid, p.lastVersion, p.lastUpdated))
      case None =>
    }
    qVersion.where(_.id is version.id).delete
  }

  def update(id: Int, sp: Version) = db withSession { implicit session =>
    val sp2update = sp.copy(Option(id))
    qVersion.where(_.id is id).update(sp2update)
  }

}