/*
 * Copyright 2026 lucf15
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef TIFFRENDERER_AFFINE_H
#define TIFFRENDERER_AFFINE_H

#include <cmath>

namespace tiffrenderer {

// A 2D affine transform, laid out to match android.graphics.Matrix#getValues(): dstX = mxx*x +
// mxy*y + mtx; dstY = myx*x + myy*y + mty. Page#render() ships us dst = M * src (page pixels ->
// bitmap pixels); rendering walks the *destination* clip rect and needs the inverse to find
// which source pixel feeds each destination pixel.
class AffineTransform {
public:
    AffineTransform() : AffineTransform(1, 0, 0, 0, 1, 0) {}

    AffineTransform(float mxx, float mxy, float mtx, float myx, float myy, float mty)
        : mxx_(mxx), mxy_(mxy), mtx_(mtx), myx_(myx), myy_(myy), mty_(mty) {}

    void apply(float x, float y, float* outX, float* outY) const {
        *outX = mxx_ * x + mxy_ * y + mtx_;
        *outY = myx_ * x + myy_ * y + mty_;
    }

    bool invert(AffineTransform* out) const {
        // std::fabs(NaN) < 1e-9f is false, so a non-finite component would otherwise slip past
        // the determinant check below undetected.
        if (!std::isfinite(mxx_) || !std::isfinite(mxy_) || !std::isfinite(mtx_)
                || !std::isfinite(myx_) || !std::isfinite(myy_) || !std::isfinite(mty_)) {
            return false;
        }
        const float det = mxx_ * myy_ - mxy_ * myx_;
        if (std::fabs(det) < 1e-9f) {
            return false;
        }
        const float invDet = 1.0f / det;
        const float outMxx = myy_ * invDet;
        const float outMxy = -mxy_ * invDet;
        const float outMyx = -myx_ * invDet;
        const float outMyy = mxx_ * invDet;
        out->mxx_ = outMxx;
        out->mxy_ = outMxy;
        out->myx_ = outMyx;
        out->myy_ = outMyy;
        out->mtx_ = -(outMxx * mtx_ + outMxy * mty_);
        out->mty_ = -(outMyx * mtx_ + outMyy * mty_);
        return true;
    }

private:
    float mxx_, mxy_, mtx_, myx_, myy_, mty_;
};

}  // namespace tiffrenderer

#endif  // TIFFRENDERER_AFFINE_H
