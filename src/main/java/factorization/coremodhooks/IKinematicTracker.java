package factorization.coremodhooks;

public interface IKinematicTracker {

    double getKinematics_motX();

    double getKinematics_motY();

    double getKinematics_motZ();

    double getKinematics_yaw();

    void reset(long now);

}